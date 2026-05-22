package com.idata.service.datax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idata.dto.DataxTaskRequest;
import com.idata.dto.DataxTaskVO;
import com.idata.entity.DataxTask;
import com.idata.entity.DatasourceConfig;
import com.idata.mapper.DataxTaskMapper;
import com.idata.service.datasource.DatasourceService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataxTaskService {

    private final DataxTaskMapper dataxTaskMapper;
    private final DatasourceService datasourceService;
    private final ObjectMapper objectMapper;

    public DataxTaskService(DataxTaskMapper dataxTaskMapper,
                            DatasourceService datasourceService,
                            ObjectMapper objectMapper) {
        this.dataxTaskMapper = dataxTaskMapper;
        this.datasourceService = datasourceService;
        this.objectMapper = objectMapper;
    }

    public List<DataxTaskVO> listAll() {
        return dataxTaskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DataxTask>()
                        .orderByDesc(DataxTask::getUpdatedAt)
                )
                .stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
    }

    public DataxTaskVO getById(Long id) {
        DataxTask task = dataxTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("DataX 任务不存在: " + id);
        }
        return toDetailVO(task);
    }

    public DataxTaskVO create(DataxTaskRequest req) {
        DataxTask task = new DataxTask();
        applyRequest(task, req);
        task.setStatus("DRAFT");
        dataxTaskMapper.insert(task);
        return toDetailVO(task);
    }

    public DataxTaskVO update(DataxTaskRequest req) {
        DataxTask task = dataxTaskMapper.selectById(req.getId());
        if (task == null) {
            throw new IllegalArgumentException("DataX 任务不存在: " + req.getId());
        }
        applyRequest(task, req);
        dataxTaskMapper.updateById(task);
        return toDetailVO(dataxTaskMapper.selectById(req.getId()));
    }

    public void delete(Long id) {
        if (dataxTaskMapper.selectById(id) == null) {
            throw new IllegalArgumentException("DataX 任务不存在: " + id);
        }
        dataxTaskMapper.deleteById(id);
    }

    public DataxTaskVO publish(Long id) {
        DataxTask task = dataxTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("DataX 任务不存在: " + id);
        }
        task.setStatus("PUBLISHED");
        dataxTaskMapper.updateById(task);
        return toDetailVO(task);
    }

    public DataxTaskVO unpublish(Long id) {
        DataxTask task = dataxTaskMapper.selectById(id);
        if (task == null) {
            throw new IllegalArgumentException("DataX 任务不存在: " + id);
        }
        task.setStatus("DRAFT");
        dataxTaskMapper.updateById(task);
        return toDetailVO(task);
    }

    public String generateDataxJson(Long id) {
        DataxTaskVO vo = getById(id);
        return buildDataxJson(vo);
    }

    // ---- internal ----

    private void applyRequest(DataxTask task, DataxTaskRequest req) {
        task.setName(req.getName());
        task.setDescription(req.getDescription());
        task.setReaderDatasourceId(req.getReaderDatasourceId());
        task.setReaderDatabase(req.getReaderDatabase());
        task.setReaderTable(req.getReaderTable());
        task.setReaderColumns(toJson(req.getReaderColumns()));
        task.setReaderWhere(req.getReaderWhere());
        task.setWriterDatasourceId(req.getWriterDatasourceId());
        task.setWriterDatabase(req.getWriterDatabase());
        task.setWriterTable(req.getWriterTable());
        task.setWriterColumns(toJson(req.getWriterColumns()));
        task.setWriteMode(req.getWriteMode() != null ? req.getWriteMode() : "insert");
        task.setFieldMappings(toJson(req.getFieldMappings()));
        task.setChannel(req.getChannel() != null ? req.getChannel() : 1);
        task.setSplitPk(req.getSplitPk());
        task.setBatchSize(req.getBatchSize() != null ? req.getBatchSize() : 1000);
        task.setEncoding(req.getEncoding() != null ? req.getEncoding() : "UTF-8");
        task.setPreSql(req.getPreSql());
        task.setPostSql(req.getPostSql());
        task.setConfigMode(req.getConfigMode() != null ? req.getConfigMode() : "UI");
        task.setScriptContent(req.getScriptContent());
    }

    private DataxTaskVO toListVO(DataxTask task) {
        DataxTaskVO vo = baseVO(task);
        // Omit field mappings in list for payload size
        vo.setFieldMappings(null);
        return vo;
    }

    private DataxTaskVO toDetailVO(DataxTask task) {
        DataxTaskVO vo = baseVO(task);
        vo.setReaderColumns(parseStringList(task.getReaderColumns()));
        vo.setWriterColumns(parseStringList(task.getWriterColumns()));
        vo.setFieldMappings(parseFieldMappings(task.getFieldMappings()));
        return vo;
    }

    @SuppressWarnings("unchecked")
    private DataxTaskVO baseVO(DataxTask task) {
        DataxTaskVO vo = new DataxTaskVO();
        vo.setId(task.getId());
        vo.setName(task.getName());
        vo.setDescription(task.getDescription());
        vo.setReaderDatasourceId(task.getReaderDatasourceId());
        vo.setReaderDatabase(task.getReaderDatabase());
        vo.setReaderTable(task.getReaderTable());
        vo.setReaderWhere(task.getReaderWhere());
        vo.setWriterDatasourceId(task.getWriterDatasourceId());
        vo.setWriterDatabase(task.getWriterDatabase());
        vo.setWriterTable(task.getWriterTable());
        vo.setWriteMode(task.getWriteMode());
        vo.setChannel(task.getChannel());
        vo.setSplitPk(task.getSplitPk());
        vo.setBatchSize(task.getBatchSize());
        vo.setEncoding(task.getEncoding());
        vo.setPreSql(task.getPreSql());
        vo.setPostSql(task.getPostSql());
        vo.setStatus(task.getStatus());
        vo.setConfigMode(task.getConfigMode());
        vo.setScriptContent(task.getScriptContent());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化失败", e);
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFieldMappings(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    // ---- DataX JSON generation ----

    private String buildDataxJson(DataxTaskVO vo) {
        // SCRIPT模式直接返回已保存的脚本内容
        if ("SCRIPT".equalsIgnoreCase(vo.getConfigMode())
                && vo.getScriptContent() != null
                && !vo.getScriptContent().isBlank()) {
            return vo.getScriptContent();
        }
        DatasourceConfig readerDs = vo.getReaderDatasourceId() != null
                ? datasourceService.getEntityById(vo.getReaderDatasourceId()) : null;
        DatasourceConfig writerDs = vo.getWriterDatasourceId() != null
                ? datasourceService.getEntityById(vo.getWriterDatasourceId()) : null;

        String readerType = readerDs != null && "HIVE".equalsIgnoreCase(readerDs.getType())
                ? "hivereader" : "mysqlreader";
        String writerType = writerDs != null && "HIVE".equalsIgnoreCase(writerDs.getType())
                ? "hivewriter" : "mysqlwriter";

        Map<String, Object> readerParam = new LinkedHashMap<>();
        Map<String, Object> writerParam = new LinkedHashMap<>();

        if (readerDs != null) {
            readerParam.put("username", readerDs.getUsername() != null ? readerDs.getUsername() : "");
            readerParam.put("password", readerDs.getPassword() != null ? readerDs.getPassword() : "");
            List<Map<String, Object>> connList = new ArrayList<>();
            Map<String, Object> conn = new LinkedHashMap<>();
            String jdbcUrl = buildJdbcUrl(readerDs, vo.getReaderDatabase());
            conn.put("jdbcUrl", new String[]{jdbcUrl});
            conn.put("table", new String[]{vo.getReaderTable()});
            connList.add(conn);
            readerParam.put("connection", connList);
			readerParam.put("column", resolveColumnsFromMapping(vo.getFieldMappings(), true, vo.getReaderColumns()));
            if (vo.getReaderWhere() != null && !vo.getReaderWhere().isBlank()) {
                readerParam.put("where", vo.getReaderWhere());
            }
            if (vo.getSplitPk() != null && !vo.getSplitPk().isBlank()) {
                readerParam.put("splitPk", vo.getSplitPk());
            }
        }

        if (writerDs != null) {
            writerParam.put("username", writerDs.getUsername() != null ? writerDs.getUsername() : "");
            writerParam.put("password", writerDs.getPassword() != null ? writerDs.getPassword() : "");
            List<Map<String, Object>> connList = new ArrayList<>();
            Map<String, Object> conn = new LinkedHashMap<>();
            String jdbcUrl = buildJdbcUrl(writerDs, vo.getWriterDatabase());
            conn.put("jdbcUrl", new String[]{jdbcUrl});
            conn.put("table", new String[]{vo.getWriterTable()});
            connList.add(conn);
            writerParam.put("connection", connList);
			writerParam.put("column", resolveColumnsFromMapping(vo.getFieldMappings(), false, vo.getWriterColumns()));
            writerParam.put("writeMode", vo.getWriteMode() != null ? vo.getWriteMode() : "insert");
            if (vo.getPreSql() != null && !vo.getPreSql().isBlank()) {
                writerParam.put("preSql", new String[]{vo.getPreSql()});
            }
            if (vo.getPostSql() != null && !vo.getPostSql().isBlank()) {
                writerParam.put("postSql", new String[]{vo.getPostSql()});
            }
            if (vo.getBatchSize() != null) {
                writerParam.put("batchSize", vo.getBatchSize());
            }
            if (vo.getEncoding() != null) {
                writerParam.put("encoding", vo.getEncoding());
            }
        }

        Map<String, Object> job = new LinkedHashMap<>();
        Map<String, Object> setting = new LinkedHashMap<>();
        Map<String, Object> speed = new LinkedHashMap<>();
        speed.put("channel", vo.getChannel() != null ? vo.getChannel() : 1);
        setting.put("speed", speed);
        job.put("setting", setting);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("reader", Map.of("name", readerType, "parameter", readerParam));
        content.put("writer", Map.of("name", writerType, "parameter", writerParam));
        contentList.add(content);
        job.put("content", contentList);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("job", job);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("生成 DataX JSON 失败", e);
        }
    }

    private String buildJdbcUrl(DatasourceConfig ds, String database) {
        String db = database != null ? database : ds.getDatabaseName();
        String host = ds.getHost() != null ? ds.getHost() : "localhost";
        int port = ds.getPort() != null ? ds.getPort() : 3306;
        if ("HIVE".equalsIgnoreCase(ds.getType())) {
            return "jdbc:hive2://" + host + ":" + port + "/" + (db != null ? db : "");
        }
        return "jdbc:mysql://" + host + ":" + port + "/" + (db != null ? db : "")
                + "?useUnicode=true&characterEncoding=utf-8&useSSL=false";
    }

    /**
     * Resolve column list from field mappings when available, falling back to defaultColumns.
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveColumnsFromMapping(List<Map<String, Object>> fieldMappings, boolean isReader, List<String> defaultColumns) {
        if (fieldMappings != null && !fieldMappings.isEmpty()) {
            String key = isReader ? "readerColumn" : "writerColumn";
            List<String> cols = fieldMappings.stream()
                    .map(m -> (String) m.get(key))
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toList());
            if (!cols.isEmpty()) {
                return cols;
            }
        }
        return defaultColumns != null ? defaultColumns : Collections.emptyList();
    }
}
