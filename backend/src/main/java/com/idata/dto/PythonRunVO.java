package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PythonRunVO {
    private Long id;
    private Long scriptId;
    private String scriptName;
    private String params;
    private String status;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
