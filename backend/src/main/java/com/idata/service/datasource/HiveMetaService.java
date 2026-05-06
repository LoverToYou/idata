package com.idata.service.datasource;

import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HiveMetaService {

    private final DatasourceService datasourceService;

    public HiveMetaService(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    /**
     * List all databases from Hive
     */
    public List<String> listDatabases(Long datasourceId) {
        List<String> databases = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                databases.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 Hive 数据库列表失败: " + e.getMessage(), e);
        }
        return databases;
    }

    /**
     * List all tables in a given database
     */
    public List<String> listTables(Long datasourceId, String databaseName) {
        List<String> tables = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId);
             Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + databaseName);
            try (ResultSet rs = stmt.executeQuery("SHOW TABLES")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 Hive 表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    /**
     * Get table schema (column name, type, comment)
     */
    public List<Map<String, String>> describeTable(Long datasourceId, String databaseName, String tableName) {
        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId);
             Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + databaseName);
            try (ResultSet rs = stmt.executeQuery("DESCRIBE " + tableName)) {
                while (rs.next()) {
                    Map<String, String> col = new HashMap<>();
                    col.put("name", rs.getString(1));
                    col.put("type", rs.getString(2));
                    col.put("comment", rs.getString(3));
                    columns.add(col);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("获取 Hive 表结构失败: " + e.getMessage(), e);
        }
        return columns;
    }

    /**
     * Get partition info for a table
     */
    public List<Map<String, String>> listPartitions(Long datasourceId, String databaseName, String tableName) {
        List<Map<String, String>> partitions = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId);
             Statement stmt = conn.createStatement()) {
            stmt.execute("USE " + databaseName);
            try (ResultSet rs = stmt.executeQuery("SHOW PARTITIONS " + tableName)) {
                while (rs.next()) {
                    Map<String, String> part = new HashMap<>();
                    part.put("partition", rs.getString(1));
                    partitions.add(part);
                }
            }
        } catch (Exception e) {
            // not all tables are partitioned
            return partitions;
        }
        return partitions;
    }
}
