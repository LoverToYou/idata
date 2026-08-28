package com.idata.service.sql;

import com.idata.dto.ExplainPlanResult;
import com.idata.dto.ExplainRow;
import com.idata.dto.SqlExecuteResult;
import com.idata.service.datasource.DatasourceService;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class SqlExecutorService {

    private final DatasourceService datasourceService;
    private final SqlSessionManager sqlSessionManager;

    public SqlExecutorService(DatasourceService datasourceService, SqlSessionManager sqlSessionManager) {
        this.datasourceService = datasourceService;
        this.sqlSessionManager = sqlSessionManager;
    }

    public SqlExecuteResult execute(Long datasourceId, String sql) {
        return execute(datasourceId, sql, null);
    }

    public SqlExecuteResult execute(Long datasourceId, String sql, String sessionId) {
        SqlExecuteResult result = new SqlExecuteResult();
        long start = System.currentTimeMillis();

        String upperSql = sql.trim().toUpperCase();
        boolean isQuery = upperSql.startsWith("SELECT") || upperSql.startsWith("SHOW")
                || upperSql.startsWith("DESCRIBE") || upperSql.startsWith("EXPLAIN");

        try {
            sqlSessionManager.run(sessionId, datasourceId, conn -> {
                try (Statement stmt = conn.createStatement()) {
                    if (isQuery) {
                        try (ResultSet rs = stmt.executeQuery(sql)) {
                            ResultSetMetaData meta = rs.getMetaData();
                            List<String> columns = new ArrayList<>();
                            for (int i = 1; i <= meta.getColumnCount(); i++) {
                                columns.add(meta.getColumnLabel(i));
                            }
                            result.setColumns(columns);

                            List<Map<String, Object>> rows = new ArrayList<>();
                            while (rs.next()) {
                                Map<String, Object> row = new LinkedHashMap<>();
                                for (String col : columns) {
                                    row.put(col, rs.getObject(col));
                                }
                                rows.add(row);
                            }
                            result.setRows(rows);
                            result.setAffectedRows(rows.size());
                        }
                    } else {
                        int affected = stmt.executeUpdate(sql);
                        result.setAffectedRows(affected);
                        result.setColumns(Collections.emptyList());
                        result.setRows(Collections.emptyList());
                    }
                }
                return null;
            });
        } catch (Exception e) {
            result.setErrorMessage(e.getMessage());
        }

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    public ExplainPlanResult explain(Long datasourceId, String sql) {
        return explain(datasourceId, sql, null);
    }

    public ExplainPlanResult explain(Long datasourceId, String sql, String sessionId) {
        ExplainPlanResult result = new ExplainPlanResult();
        long start = System.currentTimeMillis();

        try {
            sqlSessionManager.run(sessionId, datasourceId, conn -> {
                try (Statement stmt = conn.createStatement()) {
                    String configSql = "EXPLAIN " + sql;
                    try (ResultSet rs = stmt.executeQuery(configSql)) {
                        StringBuilder raw = new StringBuilder();
                        List<ExplainRow> plan = new ArrayList<>();
                        ResultSetMetaData md = rs.getMetaData();
                        if (md.getColumnCount() == 1) {
                            // Hive/Spark 等：EXPLAIN 输出为单列文本
                            while (rs.next()) {
                                raw.append(rs.getString(1)).append("\n");
                            }
                            result.setRawPlan(raw.toString());
                            // 空 plan 列表，供前端判定「有原始文本但无结构化行」
                            result.setPlan(Collections.emptyList());
                        } else {
                            // MySQL：EXPLAIN 输出为结构化多列
                            while (rs.next()) {
                            ExplainRow row = new ExplainRow();
                            row.setId(rs.getString("id"));
                            row.setSelectType(getStringSafely(rs, "select_type"));
                            row.setTable(getStringSafely(rs, "table"));
                            row.setPartitions(getStringSafely(rs, "partitions"));
                            row.setType(getStringSafely(rs, "type"));
                            row.setPossibleKeys(getStringSafely(rs, "possible_keys"));
                            row.setKey(getStringSafely(rs, "key"));
                            row.setKeyLen(getStringSafely(rs, "key_len"));
                            row.setRef(getStringSafely(rs, "ref"));
                            row.setRows(getStringSafely(rs, "rows"));
                            row.setFiltered(getStringSafely(rs, "filtered"));
                            row.setExtra(getStringSafely(rs, "Extra"));
                            plan.add(row);

                            raw.append(String.format("| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
                                    row.getId(), row.getSelectType(), row.getTable(), row.getPartitions(),
                                    row.getType(), row.getPossibleKeys(), row.getKey(), row.getKeyLen(),
                                    row.getRef(), row.getRows(), row.getExtra()));
                        }
                            result.setPlan(plan);
                            result.setRawPlan(raw.toString());
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            result.setRawPlan("EXPLAIN 失败: " + e.getMessage());
        }

        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    private String getStringSafely(ResultSet rs, String column) {
        try {
            String val = rs.getString(column);
            return val != null ? val : "";
        } catch (SQLException e) {
            return "";
        }
    }
}
