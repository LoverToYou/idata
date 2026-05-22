package com.idata.service.datasource;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JdbcMetaService {

    private final DatasourceService datasourceService;

    public JdbcMetaService(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    public List<String> listAccessibleDatabases(Long datasourceId) {
        try (Connection conn = datasourceService.getConnection(datasourceId)) {
            boolean isMySQL = conn.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            if (!isMySQL) {
                // For non-MySQL datasources (Hive etc.), fall back to listing all databases
                return listDatabases(datasourceId);
            }
            // Use information_schema.TABLES to query only databases where the MySQL user has SELECT privilege.
            // Databases without SELECT permission won't appear in information_schema.TABLES at all,
            // which is more efficient than testing each database individually via getTables().
            List<String> databases = new ArrayList<>();
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(
                     "SELECT TABLE_SCHEMA FROM information_schema.TABLES " +
                     "WHERE TABLE_TYPE LIKE '%TABLE%' OR TABLE_TYPE LIKE '%VIEW%' " +
                     "GROUP BY TABLE_SCHEMA ORDER BY TABLE_SCHEMA")) {
                while (rs.next()) {
                    databases.add(rs.getString(1));
                }
            }
            return databases;
        } catch (SQLException e) {
            throw new RuntimeException("获取可访问数据库列表失败: " + e.getMessage(), e);
        }
    }

    public List<String> listDatabases(Long datasourceId) {
        List<String> databases = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId)) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getCatalogs()) {
                while (rs.next()) {
                    databases.add(rs.getString("TABLE_CAT"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取数据库列表失败: " + e.getMessage(), e);
        }
        return databases;
    }

    public List<Map<String, String>> listTables(Long datasourceId) {
        return listTables(datasourceId, null);
    }

    public List<Map<String, String>> listTables(Long datasourceId, String database) {
        List<Map<String, String>> tables = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId)) {
            String catalog = database != null ? database : conn.getCatalog();
            if (catalog == null || catalog.isEmpty()) return tables;
            String sql = "SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES " +
                         "WHERE TABLE_SCHEMA = ? " +
                         "AND (TABLE_TYPE LIKE '%TABLE%' OR TABLE_TYPE LIKE '%VIEW%') " +
                         "ORDER BY TABLE_TYPE, TABLE_NAME";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, catalog);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> table = new HashMap<>();
                        table.put("tableSchema", rs.getString("TABLE_SCHEMA"));
                        table.put("tableName", rs.getString("TABLE_NAME"));
                        tables.add(table);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    public List<Map<String, String>> listColumns(Long datasourceId, String tableName) {
        return listColumns(datasourceId, tableName, null);
    }

    public List<Map<String, String>> listColumns(Long datasourceId, String tableName, String database) {
        List<Map<String, String>> columns = new ArrayList<>();
        try (Connection conn = datasourceService.getConnection(datasourceId)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = database != null ? database : conn.getCatalog();
            try (ResultSet rs = meta.getColumns(catalog, null, tableName, "%")) {
                while (rs.next()) {
                    Map<String, String> col = new HashMap<>();
                    col.put("name", rs.getString("COLUMN_NAME"));
                    col.put("type", rs.getString("TYPE_NAME"));
                    col.put("nullable", rs.getString("IS_NULLABLE"));
                    col.put("comment", rs.getString("REMARKS"));
                    columns.add(col);
                }
            }
            if (columns.isEmpty() && database != null) {
                // Fallback for MySQL JDBC drivers that don't work with a specific catalog
                try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
                    while (rs.next()) {
                        Map<String, String> col = new HashMap<>();
                        col.put("name", rs.getString("COLUMN_NAME"));
                        col.put("type", rs.getString("TYPE_NAME"));
                        col.put("nullable", rs.getString("IS_NULLABLE"));
                        col.put("comment", rs.getString("REMARKS"));
                        columns.add(col);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取表结构失败: " + e.getMessage(), e);
        }
        return columns;
    }

    public String getTableDdl(Long datasourceId, String tableName, String database) {
        try (Connection conn = datasourceService.getConnection(datasourceId)) {
            String catalog = database != null && !database.isEmpty() ? database : conn.getCatalog();
            String sql;
            boolean isMySQL = conn.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            if (isMySQL) {
                sql = "SHOW CREATE TABLE `" + catalog.replace("`", "``") + "`.`" + tableName.replace("`", "``") + "`";
            } else {
                sql = "SHOW CREATE TABLE " + catalog + "." + tableName;
            }
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getString(2);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取建表语句失败: " + e.getMessage(), e);
        }
        return "";
    }

    public String getTableComment(Long datasourceId, String tableName, String database) {
        try (Connection conn = datasourceService.getConnection(datasourceId)) {
            String catalog = database != null && !database.isEmpty() ? database : conn.getCatalog();
            String sql = "SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, catalog);
                stmt.setString(2, tableName);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("TABLE_COMMENT");
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取表注释失败: " + e.getMessage(), e);
        }
        return "";
    }
}
