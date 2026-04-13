package com.jobtracker.dto.dashboard;

public record DashboardSummaryResponse(
        long totalApplications,
        long waitingResponses,
        long interviewsScheduled,
        long overdueFollowUps,
        long dmRemindersEnabled
) {}
