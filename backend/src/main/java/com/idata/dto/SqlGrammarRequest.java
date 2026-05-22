package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * SQL 语法上下文检测请求。
 */
@Data
public class SqlGrammarRequest {

    /** 完整的 SQL 文本 */
    @NotBlank(message = "SQL 不能为空")
    private String sql;

    /** 光标位置（字符偏移，从 0 开始） */
    private int cursorPosition;
}
