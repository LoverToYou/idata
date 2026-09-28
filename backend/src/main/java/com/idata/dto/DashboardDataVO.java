package com.idata.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 大屏一次性取数结果：看板内所有卡片的数据。
 */
@Data
public class DashboardDataVO {

    private List<CardData> cards;

    @Data
    public static class CardData {
        private Long reportId;
        private String title;
        private String chartType;
        private String chartConfig;
        private List<String> columns;
        private List<Map<String, Object>> rows;
        private long elapsedMs;
        private String errorMessage;
    }
}
