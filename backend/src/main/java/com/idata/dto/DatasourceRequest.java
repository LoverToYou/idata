package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DatasourceRequest {
    private Long id;

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    /** MYSQL / HIVE，JDBC URL 方式下可自动推断 */
    private String type;

    private String host;

    private Integer port;

    private String databaseName;

    /** JDBC URL 直连方式（可选，填写后优先于 host/port/databaseName） */
    private String jdbcUrl;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    private String props;
}
