package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaskingRuleVO {
    private Long id;
    private String name;
    private String type;
    private String config;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
