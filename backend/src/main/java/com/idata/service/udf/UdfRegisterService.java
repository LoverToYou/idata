package com.idata.service.udf;

import com.idata.entity.UdfDefinition;
import com.idata.service.datasource.DatasourceService;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 将 UDF 注册为 Hive 永久函数（写入 metastore，跨会话持久）。
 * 注意 Hive JDBC 不支持分号分隔的多语句，必须逐句 execute。
 */
@Service
public class UdfRegisterService {

    private final DatasourceService datasourceService;

    public UdfRegisterService(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    public String qualifiedName(UdfDefinition udf) {
        return databaseOf(udf) + "." + udf.getName();
    }

    public String buildCreateSql(UdfDefinition udf) {
        return "CREATE FUNCTION " + qualifiedName(udf) + " AS '" + udf.getClassName()
                + "' USING JAR '" + udf.getJarPath() + "'";
    }

    public String buildDropSql(UdfDefinition udf) {
        return "DROP FUNCTION IF EXISTS " + qualifiedName(udf);
    }

    /** 幂等注册：先 DROP 再 CREATE，可重复执行以刷新函数实现 */
    public void register(UdfDefinition udf) throws SQLException {
        try (Connection conn = datasourceService.getConnection(udf.getDatasourceId());
             Statement stmt = conn.createStatement()) {
            stmt.execute(buildDropSql(udf));
            stmt.execute(buildCreateSql(udf));
        }
    }

    /** 注销永久函数 */
    public void unregister(UdfDefinition udf) throws SQLException {
        try (Connection conn = datasourceService.getConnection(udf.getDatasourceId());
             Statement stmt = conn.createStatement()) {
            stmt.execute(buildDropSql(udf));
        }
    }

    /** 用 DESCRIBE FUNCTION 校验函数是否存在于 metastore；函数不存在返回 false */
    public boolean exists(UdfDefinition udf) throws SQLException {
        try (Connection conn = datasourceService.getConnection(udf.getDatasourceId());
             Statement stmt = conn.createStatement()) {
            stmt.execute("DESCRIBE FUNCTION " + qualifiedName(udf));
            try (ResultSet rs = stmt.getResultSet()) {
                return rs != null && rs.next();
            }
        } catch (SQLException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("not found")) {
                return false;
            }
            throw e;
        }
    }

    private String databaseOf(UdfDefinition udf) {
        String db = udf.getDatabaseName();
        return (db == null || db.isBlank()) ? "default" : db.trim();
    }
}
