package com.jobtracker.dto.analytics;

import java.util.Map;

public record AnalyticsResponse(
        int totalApplications,
        int interviewCount,
        double interviewRate,
        int rejectionCount,
        double rejectionRate,
        int ghostingCount,
        double ghostingRate,
        double averageDaysToResponse,
        Map<String, Integer> statusBreakdown,
        Map<String, Integer> platformBreakdown
) {}
