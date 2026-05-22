package com.idata.service.sql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idata.dto.SqlTaskRequest;
import com.idata.dto.SqlTaskVO;
import com.idata.entity.DatasourceConfig;
import com.idata.entity.SqlTask;
import com.idata.mapper.DatasourceConfigMapper;
import com.idata.mapper.SqlTaskMapper;
import com.idata.service.datasource.DatasourceService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SqlTaskService {

    private final SqlTaskMapper sqlTaskMapper;
    private final DatasourceConfigMapper datasourceConfigMapper;
    private final DatasourceService datasourceService;

    public SqlTaskService(SqlTaskMapper sqlTaskMapper, DatasourceConfigMapper datasourceConfigMapper, DatasourceService datasourceService) {
        this.sqlTaskMapper = sqlTaskMapper;
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.datasourceService = datasourceService;
    }

    public List<SqlTaskVO> listAll() {
        List<SqlTask> tasks = sqlTaskMapper.selectList(
                new LambdaQueryWrapper<SqlTask>()
                        .orderByDesc(SqlTask::getUpdatedAt)
        );

        // batch test unique datasource connections
        Map<Long, Boolean> connStatus = new HashMap<>();
        tasks.stream()
                .map(SqlTask::getDatasourceId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(dsId -> {
                    try {
                        datasourceService.testConnectionById(dsId);
                        connStatus.put(dsId, true);
                    } catch (Exception e) {
                        connStatus.put(dsId, false);
                    }
                });

        return tasks.stream()
                .map(task -> toVO(task, connStatus))
                .collect(Collectors.toList());
    }

    public SqlTaskVO getById(Long id) {
        SqlTask task = sqlTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("SQL 任务不存在: " + id);
        }
        // test connection for single task
        Map<Long, Boolean> connStatus = new HashMap<>();
        if (task.getDatasourceId() != null) {
            try {
                datasourceService.testConnectionById(task.getDatasourceId());
                connStatus.put(task.getDatasourceId(), true);
            } catch (Exception e) {
                connStatus.put(task.getDatasourceId(), false);
            }
        }
        return toVO(task, connStatus);
    }

    public SqlTaskVO create(SqlTaskRequest req) {
        SqlTask task = new SqlTask();
        task.setName(req.getName());
        task.setDescription(req.getDescription());
        task.setDatasourceId(req.getDatasourceId());
        task.setSqlContent(req.getSqlContent());
        task.setSqlType(detectSqlType(req.getSqlContent()));
        task.setStatus("DRAFT");
        sqlTaskMapper.insert(task);
        return toVO(task);
    }

    public SqlTaskVO update(SqlTaskRequest req) {
        SqlTask task = sqlTaskMapper.selectById(req.getId());
        if (task == null) {
            throw new IllegalArgumentException("SQL 任务不存在: " + req.getId());
        }
        task.setName(req.getName());
        task.setDescription(req.getDescription());
        task.setDatasourceId(req.getDatasourceId());
        task.setSqlContent(req.getSqlContent());
        if (req.getSqlType() != null) {
            task.setSqlType(req.getSqlType());
        } else {
            task.setSqlType(detectSqlType(req.getSqlContent()));
        }
        sqlTaskMapper.updateById(task);
        return toVO(sqlTaskMapper.selectById(req.getId()));
    }

    public void delete(Long id) {
        if (sqlTaskMapper.selectById(id) == null) {
            throw new IllegalArgumentException("SQL 任务不存在: " + id);
        }
        sqlTaskMapper.deleteById(id);
    }

    public SqlTaskVO publish(Long id) {
        SqlTask task = sqlTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("SQL 任务不存在: " + id);
        }
        task.setStatus("PUBLISHED");
        sqlTaskMapper.updateById(task);
        return toVO(sqlTaskMapper.selectById(id));
    }

    public SqlTaskVO unpublish(Long id) {
        SqlTask task = sqlTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("SQL 任务不存在: " + id);
        }
        task.setStatus("DRAFT");
        sqlTaskMapper.updateById(task);
        return toVO(sqlTaskMapper.selectById(id));
    }

    private SqlTaskVO toVO(SqlTask task) {
        return toVO(task, Collections.emptyMap());
    }

    private SqlTaskVO toVO(SqlTask task, Map<Long, Boolean> connStatus) {
        SqlTaskVO vo = new SqlTaskVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setDatasourceId(task.getDatasourceId());
        vo.setSqlContent(task.getSqlContent());
        vo.setSqlType(task.getSqlType());
        vo.setCreatedBy(task.getCreatedBy());
        vo.setStatus(task.getStatus());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        // populate datasource type and connection status
        if (task.getDatasourceId() != null) {
            DatasourceConfig ds = datasourceConfigMapper.selectById(task.getDatasourceId());
            if (ds != null) {
                vo.setDatasourceType(ds.getType());
                vo.setDatasourceConnected(connStatus.get(task.getDatasourceId()));
            }
        }
        return vo;
    }

    private String detectSqlType(String sql) {
        if (sql == null || sql.isBlank()) return "OTHER";
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("INSERT")) return "INSERT";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        if (trimmed.startsWith("CREATE")) return "CREATE";
        if (trimmed.startsWith("ALTER")) return "ALTER";
        if (trimmed.startsWith("DROP")) return "DROP";
        if (trimmed.startsWith("TRUNCATE")) return "TRUNCATE";
        if (trimmed.startsWith("SHOW")) return "SHOW";
        if (trimmed.startsWith("DESC") || trimmed.startsWith("DESCRIBE")) return "DESCRIBE";
        return "OTHER";
    }
}
