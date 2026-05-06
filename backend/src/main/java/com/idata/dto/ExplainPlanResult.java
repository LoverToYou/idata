package com.idata.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExplainPlanResult {
    private List<ExplainRow> plan;
    private String rawPlan;
    private long elapsedMs;
}
