package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FolderRequest {
    private Long id;

    @NotBlank(message = "文件夹名称不能为空")
    private String name;

    /** 父文件夹ID，NULL=根目录 */
    private Long parentId;

    /** REPORT / DASHBOARD */
    @NotBlank(message = "业务类型不能为空")
    private String bizType;
}
