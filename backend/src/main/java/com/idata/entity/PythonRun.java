package com.idata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("python_run")
public class PythonRun {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scriptId;

    private String params;

    private String status;

    private String stdout;

    private String stderr;

    private Integer exitCode;

    private String triggeredBy;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
