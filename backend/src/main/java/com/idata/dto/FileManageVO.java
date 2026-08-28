package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileManageVO {
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileExt;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
