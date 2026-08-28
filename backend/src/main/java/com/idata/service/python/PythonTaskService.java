package com.idata.service.python;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.idata.dto.PageResult;
import com.idata.dto.PythonRunVO;
import com.idata.dto.PythonScriptRequest;
import com.idata.dto.PythonScriptVO;
import com.idata.entity.PythonRun;
import com.idata.entity.PythonScript;
import com.idata.mapper.PythonRunMapper;
import com.idata.mapper.PythonScriptMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PythonTaskService {

    private final PythonScriptMapper pythonScriptMapper;
    private final PythonRunMapper pythonRunMapper;
    private final PythonRunner pythonRunner;

    public PythonTaskService(PythonScriptMapper pythonScriptMapper,
                             PythonRunMapper pythonRunMapper,
                             PythonRunner pythonRunner) {
        this.pythonScriptMapper = pythonScriptMapper;
        this.pythonRunMapper = pythonRunMapper;
        this.pythonRunner = pythonRunner;
    }

    // ---------- 脚本 CRUD ----------

    public List<PythonScriptVO> listAll(String keyword) {
        LambdaQueryWrapper<PythonScript> wrapper = new LambdaQueryWrapper<PythonScript>()
                .orderByDesc(PythonScript::getUpdatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(PythonScript::getName, keyword);
        }
        return pythonScriptMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public PageResult<PythonScriptVO> listPage(String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<PythonScript> wrapper = new LambdaQueryWrapper<PythonScript>()
                .orderByDesc(PythonScript::getUpdatedAt);
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(PythonScript::getName, keyword);
        }
        Page<PythonScript> page = new Page<>(pageNum, pageSize);
        Page<PythonScript> result = pythonScriptMapper.selectPage(page, wrapper);
        PageResult<PythonScriptVO> pr = new PageResult<>();
        pr.setData(result.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        pr.setTotal(result.getTotal());
        pr.setPage((int) result.getCurrent());
        pr.setPageSize((int) result.getSize());
        return pr;
    }

    public PythonScriptVO getById(Long id) {
        return toVO(getScript(id));
    }

    public PythonScriptVO create(PythonScriptRequest req) {
        PythonScript script = new PythonScript();
        script.setName(req.getName());
        script.setDescription(req.getDescription());
        script.setContent(req.getContent());
        script.setTimeoutSeconds(req.getTimeoutSeconds());
        script.setStatus("DRAFT");
        script.setCreatedBy(req.getCreatedBy());
        pythonScriptMapper.insert(script);
        return toVO(script);
    }

    public PythonScriptVO update(PythonScriptRequest req) {
        PythonScript script = getScript(req.getId());
        script.setName(req.getName());
        script.setDescription(req.getDescription());
        script.setContent(req.getContent());
        script.setTimeoutSeconds(req.getTimeoutSeconds());
        pythonScriptMapper.updateById(script);
        return toVO(pythonScriptMapper.selectById(script.getId()));
    }

    public void delete(Long id) {
        getScript(id);
        pythonScriptMapper.deleteById(id);
    }

    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        pythonScriptMapper.deleteByIds(ids);
    }

    // ---------- 发布状态 ----------

    public PythonScriptVO publish(Long id) {
        PythonScript script = getScript(id);
        script.setStatus("PUBLISHED");
        pythonScriptMapper.updateById(script);
        return toVO(pythonScriptMapper.selectById(id));
    }

    public PythonScriptVO unpublish(Long id) {
        PythonScript script = getScript(id);
        script.setStatus("DRAFT");
        pythonScriptMapper.updateById(script);
        return toVO(pythonScriptMapper.selectById(id));
    }

    // ---------- 执行 ----------

    /**
     * Insert a run row and schedule execution in the background. Returns the run id.
     */
    public Long startRun(Long scriptId, String params, String triggeredBy) {
        PythonScript script = getScript(scriptId);
        PythonRun run = new PythonRun();
        run.setScriptId(scriptId);
        run.setParams(params);
        run.setStatus("RUNNING");
        run.setTriggeredBy(triggeredBy != null ? triggeredBy : "MANUAL");
        run.setStartedAt(LocalDateTime.now());
        pythonRunMapper.insert(run);
        pythonRunner.execute(script, params, run);
        return run.getId();
    }

    public PageResult<PythonRunVO> listRuns(Long scriptId, int pageNum, int pageSize) {
        LambdaQueryWrapper<PythonRun> wrapper = new LambdaQueryWrapper<PythonRun>()
                .orderByDesc(PythonRun::getId);
        if (scriptId != null) {
            wrapper.eq(PythonRun::getScriptId, scriptId);
        }
        Page<PythonRun> page = new Page<>(pageNum, pageSize);
        Page<PythonRun> result = pythonRunMapper.selectPage(page, wrapper);

        Map<Long, PythonScript> scriptMap = result.getRecords().stream()
                .map(PythonRun::getScriptId)
                .filter(Objects::nonNull)
                .distinct()
                .map(pythonScriptMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(PythonScript::getId, Function.identity()));

        PageResult<PythonRunVO> pr = new PageResult<>();
        pr.setData(result.getRecords().stream()
                .map(r -> toRunVO(r, scriptMap.get(r.getScriptId())))
                .collect(Collectors.toList()));
        pr.setTotal(result.getTotal());
        pr.setPage((int) result.getCurrent());
        pr.setPageSize((int) result.getSize());
        return pr;
    }

    public PythonRunVO getRun(Long runId) {
        PythonRun run = pythonRunMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("执行记录不存在: " + runId);
        }
        PythonScript script = run.getScriptId() != null ? pythonScriptMapper.selectById(run.getScriptId()) : null;
        return toRunVO(run, script);
    }

    public void cancelRun(Long runId) {
        PythonRun run = pythonRunMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("执行记录不存在: " + runId);
        }
        if (!"RUNNING".equals(run.getStatus())) {
            return;
        }
        PythonRun update = new PythonRun();
        update.setId(runId);
        update.setStatus("CANCELLED");
        update.setFinishedAt(LocalDateTime.now());
        pythonRunMapper.updateById(update);
        pythonRunner.kill(runId);
    }

    public PythonScript getScriptEntity(Long id) {
        return getScript(id);
    }

    /**
     * Execute a script synchronously (workflow node use). Creates a run row,
     * runs to completion, and returns the final run row.
     */
    public PythonRun runSync(Long scriptId, String params) {
        PythonScript script = getScript(scriptId);
        PythonRun run = new PythonRun();
        run.setScriptId(scriptId);
        run.setParams(params);
        run.setStatus("RUNNING");
        run.setTriggeredBy("WORKFLOW");
        run.setStartedAt(LocalDateTime.now());
        pythonRunMapper.insert(run);
        return pythonRunner.executeSync(script, params, run);
    }

    private PythonScript getScript(Long id) {
        PythonScript script = pythonScriptMapper.selectById(id);
        if (script == null) {
            throw new IllegalArgumentException("脚本不存在: " + id);
        }
        return script;
    }

    private PythonScriptVO toVO(PythonScript script) {
        PythonScriptVO vo = new PythonScriptVO();
        vo.setId(script.getId());
        vo.setName(script.getName());
        vo.setDescription(script.getDescription());
        vo.setContent(script.getContent());
        vo.setTimeoutSeconds(script.getTimeoutSeconds());
        vo.setStatus(script.getStatus());
        vo.setCreatedBy(script.getCreatedBy());
        vo.setCreatedAt(script.getCreatedAt());
        vo.setUpdatedAt(script.getUpdatedAt());
        return vo;
    }

    private PythonRunVO toRunVO(PythonRun run, PythonScript script) {
        PythonRunVO vo = new PythonRunVO();
        vo.setId(run.getId());
        vo.setScriptId(run.getScriptId());
        vo.setScriptName(script != null ? script.getName() : null);
        vo.setParams(run.getParams());
        vo.setStatus(run.getStatus());
        vo.setStdout(run.getStdout());
        vo.setStderr(run.getStderr());
        vo.setExitCode(run.getExitCode());
        vo.setTriggeredBy(run.getTriggeredBy());
        vo.setStartedAt(run.getStartedAt());
        vo.setFinishedAt(run.getFinishedAt());
        return vo;
    }
}
