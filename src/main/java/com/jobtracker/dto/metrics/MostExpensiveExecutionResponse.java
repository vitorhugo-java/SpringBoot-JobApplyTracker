package com.jobtracker.dto.metrics;

import java.time.LocalDateTime;
import java.util.UUID;

public record MostExpensiveExecutionResponse(
        UUID id,
        String toolName,
        long executionTimeMs,
        int requestBytes,
        int responseBytes,
        int requestTokens,
        int responseTokens,
        int totalTokens,
        boolean expensive,
        LocalDateTime createdAt
) {}
