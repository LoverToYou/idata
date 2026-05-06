package com.idata.engine.datax;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Builds DataX job configuration JSON from node configuration maps.
 * Supports: mysqlreader, mysqlwriter, hivereader, hivewriter.
 */
public class DataXConfigBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Build a complete DataX JSON configuration string.
     *
     * @param readerConfig reader/source node configuration
     * @param writerConfig writer/sink node configuration
     * @return DataX JSON string
     */
    public static String build(Map<String, Object> readerConfig, Map<String, Object> writerConfig) {
        try {
            Map<String, Object> job = new LinkedHashMap<>();

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("reader", buildReaderOrWriter(readerConfig));
            content.put("writer", buildReaderOrWriter(writerConfig));

            List<Map<String, Object>> contentList = new ArrayList<>();
            contentList.add(content);

            Map<String, Object> setting = new LinkedHashMap<>();
            Map<String, Object> speed = new LinkedHashMap<>();
            speed.put("channel", 1);
            setting.put("speed", speed);

            job.put("content", contentList);
            job.put("setting", setting);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("job", job);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build DataX config JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildReaderOrWriter(Map<String, Object> nodeConfig) {
        if (nodeConfig == null) {
            throw new IllegalArgumentException("Node config must not be null");
        }

        String type = (String) nodeConfig.get("type");
        if (type == null) {
            throw new IllegalArgumentException("Node config must have a 'type' field");
        }

        Map<String, Object> parameter = new LinkedHashMap<>();
        String name;

        switch (type.toLowerCase()) {
            case "mysqlreader":
                name = "mysqlreader";
                buildMysqlReaderParameter(parameter, nodeConfig);
                break;
            case "mysqlwriter":
                name = "mysqlwriter";
                buildMysqlWriterParameter(parameter, nodeConfig);
                break;
            case "hivereader":
                name = "hivereader";
                buildHiveReaderParameter(parameter, nodeConfig);
                break;
            case "hivewriter":
                name = "hivewriter";
                buildHiveWriterParameter(parameter, nodeConfig);
                break;
            default:
                throw new IllegalArgumentException("Unsupported DataX type: " + type);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("parameter", parameter);
        return result;
    }

    private static void buildMysqlReaderParameter(Map<String, Object> parameter, Map<String, Object> config) {
        String username = getString(config, "username");
        String password = getString(config, "password");
        String jdbcUrl = getString(config, "jdbcUrl");
        String table = getString(config, "table");
        String column = getString(config, "column", "*");
        String splitPk = getString(config, "splitPk", "");

        Map<String, Object> connection = new LinkedHashMap<>();
        List<String> jdbcUrlList = new ArrayList<>();
        jdbcUrlList.add(jdbcUrl);
        connection.put("jdbcUrl", jdbcUrlList);

        List<String> tableList = new ArrayList<>();
        tableList.add(table);
        connection.put("table", tableList);

        List<Map<String, Object>> connectionList = new ArrayList<>();
        connectionList.add(connection);

        parameter.put("username", username);
        parameter.put("password", password);
        parameter.put("connection", connectionList);

        if (column != null) {
            List<String> columnList = new ArrayList<>();
            columnList.add(column);
            parameter.put("column", columnList);
        }

        if (splitPk != null && !splitPk.isEmpty()) {
            parameter.put("splitPk", splitPk);
        }

        // Optional where clause
        String where = getString(config, "where");
        if (where != null && !where.isEmpty()) {
            parameter.put("where", where);
        }
    }

    private static void buildMysqlWriterParameter(Map<String, Object> parameter, Map<String, Object> config) {
        String username = getString(config, "username");
        String password = getString(config, "password");
        String jdbcUrl = getString(config, "jdbcUrl");
        String table = getString(config, "table");
        String column = getString(config, "column", "*");

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("jdbcUrl", jdbcUrl);

        List<String> tableList = new ArrayList<>();
        tableList.add(table);
        connection.put("table", tableList);

        List<Map<String, Object>> connectionList = new ArrayList<>();
        connectionList.add(connection);

        parameter.put("username", username);
        parameter.put("password", password);
        parameter.put("connection", connectionList);

        if (column != null) {
            List<String> columnList = new ArrayList<>();
            columnList.add(column);
            parameter.put("column", columnList);
        }

        // Optional pre/post SQL
        String preSql = getString(config, "preSql");
        if (preSql != null && !preSql.isEmpty()) {
            List<String> preSqlList = new ArrayList<>();
            preSqlList.add(preSql);
            parameter.put("preSql", preSqlList);
        }

        String postSql = getString(config, "postSql");
        if (postSql != null && !postSql.isEmpty()) {
            List<String> postSqlList = new ArrayList<>();
            postSqlList.add(postSql);
            parameter.put("postSql", postSqlList);
        }
    }

    private static void buildHiveReaderParameter(Map<String, Object> parameter, Map<String, Object> config) {
        // Hive uses HiveWriter-compatible config; for reading, treat as HiveReader
        String username = getString(config, "username");
        String password = getString(config, "password");
        String jdbcUrl = getString(config, "jdbcUrl");
        String table = getString(config, "table");
        String column = getString(config, "column", "*");

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("jdbcUrl", jdbcUrl);

        List<String> tableList = new ArrayList<>();
        tableList.add(table);
        connection.put("table", tableList);

        List<Map<String, Object>> connectionList = new ArrayList<>();
        connectionList.add(connection);

        parameter.put("username", username);
        parameter.put("password", password);
        parameter.put("connection", connectionList);

        List<String> columnList = new ArrayList<>();
        columnList.add(column);
        parameter.put("column", columnList);

        // Hive partition
        String partition = getString(config, "partition");
        if (partition != null && !partition.isEmpty()) {
            parameter.put("partition", partition);
        }
    }

    private static void buildHiveWriterParameter(Map<String, Object> parameter, Map<String, Object> config) {
        String username = getString(config, "username");
        String password = getString(config, "password");
        String jdbcUrl = getString(config, "jdbcUrl");
        String table = getString(config, "table");
        String column = getString(config, "column", "*");

        Map<String, Object> connection = new LinkedHashMap<>();
        connection.put("jdbcUrl", jdbcUrl);

        List<String> tableList = new ArrayList<>();
        tableList.add(table);
        connection.put("table", tableList);

        List<Map<String, Object>> connectionList = new ArrayList<>();
        connectionList.add(connection);

        parameter.put("username", username);
        parameter.put("password", password);
        parameter.put("connection", connectionList);

        List<String> columnList = new ArrayList<>();
        columnList.add(column);
        parameter.put("column", columnList);

        // Hive partition
        String partition = getString(config, "partition");
        if (partition != null && !partition.isEmpty()) {
            parameter.put("partition", partition);
        }

        // Write mode: insert/overwrite
        String writeMode = getString(config, "writeMode", "insert");
        parameter.put("writeMode", writeMode);
    }

    private static String getString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value != null ? value.toString() : null;
    }

    private static String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
