package com.idata.service.sql;

import com.idata.dto.ExplainPlanResult;
import com.idata.dto.ExplainRow;
import com.idata.dto.SqlSuggestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SqlOptimizerService {

    public List<SqlSuggestion> analyzePlan(ExplainPlanResult planResult) {
        List<SqlSuggestion> suggestions = new ArrayList<>();

        if (planResult.getPlan() == null || planResult.getPlan().isEmpty()) {
            return suggestions;
        }

        for (ExplainRow row : planResult.getPlan()) {
            // Check full table scan
            if ("ALL".equalsIgnoreCase(row.getType())) {
                SqlSuggestion s = new SqlSuggestion();
                s.setType("WARNING");
                s.setTitle("全表扫描");
                s.setDetail(String.format("表 '%s' 使用了全表扫描 (type=ALL)，预计扫描 %s 行。",
                        row.getTable(), row.getRows()));
                s.setSuggestion("建议在 WHERE 条件涉及的字段上添加索引。");
                suggestions.add(s);
            }

            // Check no index usage
            if (row.getPossibleKeys() != null && !row.getPossibleKeys().isEmpty()
                    && (row.getKey() == null || row.getKey().isEmpty())) {
                SqlSuggestion s = new SqlSuggestion();
                s.setType("INFO");
                s.setTitle("未使用可用索引");
                s.setDetail(String.format("表 '%s' 有可用索引 (%s) 但未使用。",
                        row.getTable(), row.getPossibleKeys()));
                s.setSuggestion("检查查询条件是否导致索引失效（如函数运算、隐式类型转换）。");
                suggestions.add(s);
            }

            // Check using temporary
            String extra = row.getExtra();
            if (extra != null) {
                if (extra.contains("Using temporary")) {
                    SqlSuggestion s = new SqlSuggestion();
                    s.setType("WARNING");
                    s.setTitle("使用了临时表");
                    s.setDetail(String.format("表 '%s' 的查询使用了临时表，通常由 GROUP BY 或 DISTINCT 导致。", row.getTable()));
                    s.setSuggestion("考虑添加合适的索引来避免临时表。");
                    suggestions.add(s);
                }

                if (extra.contains("Using filesort")) {
                    SqlSuggestion s = new SqlSuggestion();
                    s.setType("WARNING");
                    s.setTitle("使用了文件排序");
                    s.setDetail(String.format("表 '%s' 的查询使用了文件排序 (filesort)。", row.getTable()));
                    s.setSuggestion("为 ORDER BY 涉及的字段添加索引可以直接利用索引排序。");
                    suggestions.add(s);
                }

                if (extra.contains("Using index condition")) {
                    SqlSuggestion s = new SqlSuggestion();
                    s.setType("INFO");
                    s.setTitle("索引条件下推 (ICP)");
                    s.setDetail(String.format("表 '%s' 使用了索引条件下推优化。", row.getTable()));
                    s.setSuggestion("当前查询已经较优，继续保持。");
                    suggestions.add(s);
                }
            }

            // Check good patterns
            if ("ref".equalsIgnoreCase(row.getType()) || "eq_ref".equalsIgnoreCase(row.getType())) {
                SqlSuggestion s = new SqlSuggestion();
                s.setType("INFO");
                s.setTitle("索引查询");
                s.setDetail(String.format("表 '%s' 使用了索引查找 (type=%s)，效率较高。",
                        row.getTable(), row.getType()));
                s.setSuggestion("当前查询方式良好。");
                suggestions.add(s);
            }

            if ("const".equalsIgnoreCase(row.getType())) {
                SqlSuggestion s = new SqlSuggestion();
                s.setType("INFO");
                s.setTitle("常量查询");
                s.setDetail(String.format("表 '%s' 使用了常量查找 (type=const)，最高效的方式。", row.getTable()));
                s.setSuggestion("极优。");
                suggestions.add(s);
            }
        }

        return suggestions;
    }
}
