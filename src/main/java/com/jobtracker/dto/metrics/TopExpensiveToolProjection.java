package com.jobtracker.dto.metrics;

public interface TopExpensiveToolProjection {
    String getToolName();
    Long getCalls();
    Double getAvgTokens();
    Long getMaxTokens();
}
