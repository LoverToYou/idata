package com.idata.engine.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idata.entity.NodeExecutionLog;
import com.idata.entity.WorkflowDefinition;
import com.idata.entity.WorkflowInstance;
import com.idata.mapper.NodeExecutionLogMapper;
import com.idata.mapper.WorkflowDefinitionMapper;
import com.idata.service.workflow.WorkflowInstanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Executes a workflow DAG by topological sorting its nodes and running them in order.
 */
@Component
public class DagExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagExecutor.class);

    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowInstanceService workflowInstanceService;
    private final NodeExecutionLogMapper nodeExecutionLogMapper;
    private final ObjectMapper objectMapper;

    public DagExecutor(WorkflowDefinitionMapper workflowDefinitionMapper,
                       WorkflowInstanceService workflowInstanceService,
                       NodeExecutionLogMapper nodeExecutionLogMapper,
                       ObjectMapper objectMapper) {
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowInstanceService = workflowInstanceService;
        this.nodeExecutionLogMapper = nodeExecutionLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute a workflow DAG. Creates a WorkflowInstance and runs all nodes in topological order.
     *
     * @param workflowId the workflow definition ID
     * @param triggeredBy source of trigger ("MANUAL" or "CRON")
     * @return the created WorkflowInstance ID
     */
    public Long execute(Long workflowId, String triggeredBy) {
        // 1. Load workflow definition
        WorkflowDefinition def = workflowDefinitionMapper.selectById(workflowId);
        if (def == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        String dagJson = def.getDagJson();
        if (dagJson == null || dagJson.isBlank() || "{}".equals(dagJson.trim())) {
            throw new IllegalArgumentException("工作流DAG定义为空: " + workflowId);
        }

        // 2. Parse DAG JSON
        Map<String, Object> dag;
        try {
            dag = objectMapper.readValue(dagJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("工作流DAG定义解析失败: " + e.getMessage(), e);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) dag.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) dag.get("edges");

        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("工作流没有节点: " + workflowId);
        }

        // 3. Create workflow instance
        WorkflowInstance instance = workflowInstanceService.createInstance(workflowId, triggeredBy);
        Long instanceId = instance.getId();
        log.info("Created workflow instance {} for workflow {} (triggered by {})",
                instanceId, workflowId, triggeredBy);

        try {
            // 4. Build graph: nodeId -> node config map, adjacency list, in-degree map
            Map<String, Map<String, Object>> nodeMap = new LinkedHashMap<>();
            Map<String, List<String>> adjacency = new HashMap<>();
            Map<String, Integer> inDegree = new HashMap<>();

            for (Map<String, Object> node : nodes) {
                String nodeId = (String) node.get("id");
                nodeMap.put(nodeId, node);
                adjacency.putIfAbsent(nodeId, new ArrayList<>());
                inDegree.putIfAbsent(nodeId, 0);
            }

            if (edges != null) {
                for (Map<String, Object> edge : edges) {
                    String from = (String) edge.get("from");
                    String to = (String) edge.get("to");
                    if (from == null || to == null) {
                        log.warn("Skipping edge with missing from/to: {}", edge);
                        continue;
                    }
                    if (!nodeMap.containsKey(from)) {
                        log.warn("Skipping edge: source node {} not found in nodes", from);
                        continue;
                    }
                    if (!nodeMap.containsKey(to)) {
                        log.warn("Skipping edge: target node {} not found in nodes", to);
                        continue;
                    }
                    adjacency.get(from).add(to);
                    inDegree.merge(to, 1, Integer::sum);
                }
            }

            // 5. Topological sort using Kahn's algorithm
            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.add(entry.getKey());
                }
            }

            List<String> sorted = new ArrayList<>();
            while (!queue.isEmpty()) {
                String nodeId = queue.poll();
                sorted.add(nodeId);
                for (String neighbor : adjacency.getOrDefault(nodeId, Collections.emptyList())) {
                    int newDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, newDegree);
                    if (newDegree == 0) {
                        queue.add(neighbor);
                    }
                }
            }

            // 6. Check for cycles
            if (sorted.size() != nodes.size()) {
                List<String> unsorted = nodes.stream()
                        .map(n -> (String) n.get("id"))
                        .filter(id -> !sorted.contains(id))
                        .collect(Collectors.toList());
                String errorMsg = "DAG中存在循环依赖，无法排序的节点: " + unsorted;
                log.error(errorMsg);
                workflowInstanceService.completeInstance(instanceId, "FAILED", errorMsg);
                return instanceId;
            }

            log.info("Topological order for workflow {}: {}", workflowId, sorted);

            // 7. Execute nodes in topological order
            boolean allSuccess = true;
            for (String nodeId : sorted) {
                Map<String, Object> nodeConfig = nodeMap.get(nodeId);
                String nodeName = nodeConfig != null
                        ? (String) nodeConfig.getOrDefault("label", nodeId)
                        : nodeId;

                // Create WAITING node log
                NodeExecutionLog nodeLog = new NodeExecutionLog();
                nodeLog.setInstanceId(instanceId);
                nodeLog.setNodeId(nodeId);
                nodeLog.setNodeName(nodeName);
                nodeLog.setStatus("WAITING");
                nodeExecutionLogMapper.insert(nodeLog);

                try {
                    // Transition to RUNNING
                    nodeLog.setStatus("RUNNING");
                    nodeLog.setStartedAt(LocalDateTime.now());
                    nodeExecutionLogMapper.updateById(nodeLog);

                    log.info("Executing node: {} ({}) in instance {}", nodeName, nodeId, instanceId);

                    // TODO: In the future, dispatch to DataXRunner or other executors
                    // based on nodeConfig.get("type") and nodeConfig.get("config")
                    executeNode(nodeConfig, instanceId);

                    // Transition to SUCCESS
                    nodeLog.setStatus("SUCCESS");
                    nodeLog.setFinishedAt(LocalDateTime.now());
                    nodeExecutionLogMapper.updateById(nodeLog);

                    log.info("Node {} completed successfully", nodeId);

                } catch (Exception e) {
                    log.error("Node {} failed: {}", nodeId, e.getMessage(), e);
                    nodeLog.setStatus("FAILED");
                    nodeLog.setFinishedAt(LocalDateTime.now());
                    nodeLog.setErrorMessage(e.getMessage());
                    nodeExecutionLogMapper.updateById(nodeLog);

                    allSuccess = false;
                    break;
                }
            }

            // 8. Complete the instance
            if (allSuccess) {
                workflowInstanceService.completeInstance(instanceId, "SUCCESS", null);
                log.info("Workflow instance {} completed successfully", instanceId);
            } else {
                workflowInstanceService.completeInstance(instanceId, "FAILED", "节点执行失败");
                log.info("Workflow instance {} failed", instanceId);
            }

        } catch (Exception e) {
            log.error("Workflow instance {} execution error: {}", instanceId, e.getMessage(), e);
            workflowInstanceService.completeInstance(instanceId, "FAILED", e.getMessage());
        }

        return instanceId;
    }

    /**
     * Execute a single DAG node. For now this is a stub that simulates execution.
     * In the future, this will dispatch to DataXRunner or other executors based on node type.
     */
    private void executeNode(Map<String, Object> nodeConfig, Long instanceId) {
        // Stub: simulate execution
        // TODO: check node type and dispatch accordingly
        log.debug("Simulating execution of node {} in instance {}", nodeConfig.get("id"), instanceId);
    }
}
