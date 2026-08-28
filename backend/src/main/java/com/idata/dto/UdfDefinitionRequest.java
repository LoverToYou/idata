package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UdfDefinitionRequest {
    private Long id;

    @NotBlank(message = "函数名不能为空")
    private String name;

    @NotBlank(message = "实现类全限定名不能为空")
    private String className;

    /** 关联文件管理记录；与 jarPath 二选一 */
    private Long fileId;

    /** 手动指定 JAR 的 HDFS 路径（如 hdfs://127.0.0.1:9000/data/x.jar）；与 fileId 二选一，优先使用 */
    private String jarPath;

    private String databaseName;

    @NotNull(message = "数据源不能为空")
    private Long datasourceId;

    private String functionType;

    private String description;
}
