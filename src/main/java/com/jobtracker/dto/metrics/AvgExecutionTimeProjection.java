package com.jobtracker.dto.metrics;

public interface AvgExecutionTimeProjection {
    String getToolName();
    Double getAvgExecutionTimeMs();
    Long getCalls();
}
