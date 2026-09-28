package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FolderVO {
    private Long id;
    private String name;
    private Long parentId;
    private String bizType;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
