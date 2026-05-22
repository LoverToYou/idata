package com.idata.service.workflow;

import com.idata.dto.WorkflowRequest;
import com.idata.dto.WorkflowVO;
import com.idata.entity.WorkflowDefinition;
import com.idata.mapper.WorkflowDefinitionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkflowService {

    private final WorkflowDefinitionMapper workflowDefinitionMapper;

    public WorkflowService(WorkflowDefinitionMapper workflowDefinitionMapper) {
        this.workflowDefinitionMapper = workflowDefinitionMapper;
    }

    public List<WorkflowVO> listAll() {
        return workflowDefinitionMapper.selectList(null)
                .stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
    }

    public WorkflowVO getById(Long id) {
        WorkflowDefinition def = workflowDefinitionMapper.selectById(id);
        if (def == null) {
            throw new IllegalArgumentException("工作流不存在: " + id);
        }
        return toDetailVO(def);
    }

    public WorkflowVO create(WorkflowRequest req) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setName(req.getName());
        def.setDescription(req.getDescription());
        def.setDagJson(req.getDagJson() != null ? req.getDagJson() : "{}");
        def.setEtlType(req.getEtlType() != null ? req.getEtlType() : "DATAX");
        def.setStatus("DRAFT");
        workflowDefinitionMapper.insert(def);
        return toDetailVO(def);
    }

    public WorkflowVO update(WorkflowRequest req) {
        WorkflowDefinition def = workflowDefinitionMapper.selectById(req.getId());
        if (def == null) {
            throw new IllegalArgumentException("工作流不存在: " + req.getId());
        }
        def.setName(req.getName());
        def.setDescription(req.getDescription());
        def.setDagJson(req.getDagJson());
        def.setEtlType(req.getEtlType());
        workflowDefinitionMapper.updateById(def);
        return toDetailVO(workflowDefinitionMapper.selectById(req.getId()));
    }

    public void delete(Long id) {
        if (workflowDefinitionMapper.selectById(id) == null) {
            throw new IllegalArgumentException("工作流不存在: " + id);
        }
        workflowDefinitionMapper.deleteById(id);
    }

    public WorkflowVO publish(Long id) {
        WorkflowDefinition def = workflowDefinitionMapper.selectById(id);
        if (def == null) {
            throw new IllegalArgumentException("工作流不存在: " + id);
        }
        def.setStatus("PUBLISHED");
        workflowDefinitionMapper.updateById(def);
        return toDetailVO(workflowDefinitionMapper.selectById(id));
    }

    public WorkflowVO unpublish(Long id) {
        WorkflowDefinition def = workflowDefinitionMapper.selectById(id);
        if (def == null) {
            throw new IllegalArgumentException("工作流不存在: " + id);
        }
        def.setStatus("DRAFT");
        workflowDefinitionMapper.updateById(def);
        return toDetailVO(workflowDefinitionMapper.selectById(id));
    }

    private WorkflowVO toListVO(WorkflowDefinition def) {
        WorkflowVO vo = new WorkflowVO();
        vo.setId(def.getId());
        vo.setName(def.getName());
        vo.setDescription(def.getDescription());
        vo.setStatus(def.getStatus());
        vo.setEtlType(def.getEtlType());
        vo.setCreatedAt(def.getCreatedAt());
        vo.setUpdatedAt(def.getUpdatedAt());
        return vo;
    }

    private WorkflowVO toDetailVO(WorkflowDefinition def) {
        WorkflowVO vo = new WorkflowVO();
        vo.setId(def.getId());
        vo.setName(def.getName());
        vo.setDescription(def.getDescription());
        vo.setDagJson(def.getDagJson());
        vo.setStatus(def.getStatus());
        vo.setEtlType(def.getEtlType());
        vo.setCreatedAt(def.getCreatedAt());
        vo.setUpdatedAt(def.getUpdatedAt());
        return vo;
    }
}
