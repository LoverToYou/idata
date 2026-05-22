package com.idata.engine.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idata.entity.NodeExecutionLog;
import com.idata.entity.WorkflowDefinition;
import com.idata.entity.WorkflowInstance;
import com.idata.mapper.NodeExecutionLogMapper;
import com.idata.mapper.WorkflowDefinitionMapper;
import com.idata.engine.datax.DataXRunner;
import com.idata.dto.DataxTaskVO;
import com.idata.service.datax.DataxTaskService;
import com.idata.service.sql.ParameterService;
import com.idata.service.sql.SqlExecutorService;
import com.idata.service.sql.SqlTaskService;
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
    private final SqlTaskService sqlTaskService;
    private final ParameterService parameterService;
    private final SqlExecutorService sqlExecutorService;
    private final DataXRunner dataXRunner;
    private final DataxTaskService dataxTaskService;

    public DagExecutor(WorkflowDefinitionMapper workflowDefinitionMapper,
                       WorkflowInstanceService workflowInstanceService,
                       NodeExecutionLogMapper nodeExecutionLogMapper,
                       ObjectMapper objectMapper,
                       SqlTaskService sqlTaskService,
                       ParameterService parameterService,
                       SqlExecutorService sqlExecutorService,
                       DataXRunner dataXRunner,
                       DataxTaskService dataxTaskService) {
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowInstanceService = workflowInstanceService;
        this.nodeExecutionLogMapper = nodeExecutionLogMapper;
        this.objectMapper = objectMapper;
        this.sqlTaskService = sqlTaskService;
        this.parameterService = parameterService;
        this.sqlExecutorService = sqlExecutorService;
        this.dataXRunner = dataXRunner;
        this.dataxTaskService = dataxTaskService;
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

        // 2a. Support new config-mode format (GUI/script) — build synthetic DAG
        String configMode = (String) dag.get("configMode");
        List<Map<String, Object>> nodes;
        List<Map<String, Object>> edges;

        if (configMode != null) {
            // New editor format: single synthetic node wrapping the DataX JSON
            String dataxJson;
            if ("script".equals(configMode)) {
                dataxJson = (String) dag.get("script");
            } else {
                dataxJson = (String) dag.get("dataxJson");
            }
            if (dataxJson == null || dataxJson.isBlank()) {
                dataxJson = "{}";
            }
            Map<String, Object> synNode = new LinkedHashMap<>();
            synNode.put("id", "datax-1");
            synNode.put("label", "DataX 任务");
            synNode.put("type", "datax");
            Map<String, Object> synConfig = new LinkedHashMap<>();
            synConfig.put("dataxJson", dataxJson);
            synNode.put("config", synConfig);
            nodes = Collections.singletonList(synNode);
            edges = Collections.emptyList();
        } else {
            // Legacy DAG format (nodes / edges)
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) dag.get("nodes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawEdges = (List<Map<String, Object>>) dag.get("edges");
            nodes = rawNodes;
            edges = rawEdges;
        }

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

                    executeNode(nodeConfig, nodeLog);

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
     * Execute a single DAG node. Dispatches to the appropriate executor based on node type.
     */
    private void executeNode(Map<String, Object> nodeConfig, NodeExecutionLog nodeLog) {
        String nodeType = (String) nodeConfig.get("type");
        if (nodeType == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) nodeConfig.get("config");
            if (config != null) {
                nodeType = (String) config.get("type");
            }
        }

        if ("sql_task".equals(nodeType)) {
            executeSqlTaskNode(nodeConfig, nodeLog.getInstanceId());
        } else if ("datax".equals(nodeType)) {
            executeDataxNode(nodeConfig, nodeLog);
        } else {
            log.warn("Unknown node type '{}' for node {} in instance {}, skipping",
                    nodeType, nodeConfig.get("id"), nodeLog.getInstanceId());
        }
    }

    @SuppressWarnings("unchecked")
    private void executeDataxNode(Map<String, Object> nodeConfig, NodeExecutionLog nodeLog) {
        Map<String, Object> config = (Map<String, Object>) nodeConfig.get("config");
        if (config == null) {
            throw new IllegalArgumentException("DataX 任务节点缺少 config: " + nodeConfig.get("id"));
        }

        Object dataxTaskIdObj = config.get("dataxTaskId");
        if (dataxTaskIdObj == null) {
            throw new IllegalArgumentException("DataX 任务节点缺少 dataxTaskId: " + nodeConfig.get("id"));
        }

        Long dataxTaskId;
        if (dataxTaskIdObj instanceof Number) {
            dataxTaskId = ((Number) dataxTaskIdObj).longValue();
        } else {
            dataxTaskId = Long.valueOf(dataxTaskIdObj.toString());
        }

        // 1. Load the DataX task and generate JSON config
        DataxTaskVO task = dataxTaskService.getById(dataxTaskId);
        log.info("Executing DataX task {} (id={}) in instance {}", task.getName(), dataxTaskId, nodeLog.getInstanceId());

        String dataxJson = dataxTaskService.generateDataxJson(dataxTaskId);
        nodeLog.setDataxJson(dataxJson);

        // 2. Attempt to run DataX via DataXRunner
        executeDataxRunner(dataxJson, nodeConfig, nodeLog);
        log.info("DataX task {} completed", task.getName());
    }

    private void executeDataxRunner(String dataxJson, Map<String, Object> nodeConfig, NodeExecutionLog nodeLog) {
        try {
            var result = dataXRunner.execute(dataxJson);
            String output = "退出码: " + result.getExitCode()
                    + "\n\n===== 标准输出 =====\n" + result.getStdout()
                    + "\n===== 标准错误 =====\n" + result.getStderr();
            nodeLog.setOutputLog(output);

            if (!result.isSuccess()) {
                throw new RuntimeException("DataX 任务执行失败，退出码: " + result.getExitCode()
                        + "，错误信息: " + (result.getStderr() != null ? result.getStderr().trim() : "无"));
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("I/O error")) {
                log.warn("DataX runner unavailable for node {} in instance {}, skipping execution. Config generated.",
                        nodeConfig.get("id"), nodeLog.getInstanceId());
                nodeLog.setOutputLog("[DataX] 运行环境不可用，已生成配置文件但未执行同步\n\n===== 生成的 DataX 配置 =====\n" + dataxJson);
            } else {
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void executeSqlTaskNode(Map<String, Object> nodeConfig, Long instanceId) {
        Map<String, Object> config = (Map<String, Object>) nodeConfig.get("config");
        if (config == null) {
            throw new IllegalArgumentException("SQL 任务节点缺少 config: " + nodeConfig.get("id"));
        }

        Object sqlTaskIdObj = config.get("sqlTaskId");
        if (sqlTaskIdObj == null) {
            throw new IllegalArgumentException("SQL 任务节点缺少 sqlTaskId: " + nodeConfig.get("id"));
        }

        Long sqlTaskId;
        if (sqlTaskIdObj instanceof Number) {
            sqlTaskId = ((Number) sqlTaskIdObj).longValue();
        } else {
            sqlTaskId = Long.valueOf(sqlTaskIdObj.toString());
        }

        // Load the SQL task
        var task = sqlTaskService.getById(sqlTaskId);

        // Resolve parameters in the SQL content
        String resolvedSql = parameterService.resolveParams(task.getSqlContent());
        log.info("Executing SQL task {} (id={}) in instance {}: {}", task.getName(), sqlTaskId, instanceId, resolvedSql);

        // Execute the SQL
        var result = sqlExecutorService.execute(task.getDatasourceId(), resolvedSql);
        if (result.getErrorMessage() != null) {
            throw new RuntimeException("SQL 任务执行失败: " + result.getErrorMessage());
        }
        log.info("SQL task {} completed: {} rows affected, {}ms",
                task.getName(), result.getAffectedRows(), result.getElapsedMs());
    }
}
