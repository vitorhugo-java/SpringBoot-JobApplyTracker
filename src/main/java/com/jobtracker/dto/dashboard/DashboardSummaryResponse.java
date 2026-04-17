package com.jobtracker.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregate dashboard statistics for the authenticated user")
public record DashboardSummaryResponse(
        @Schema(description = "Total number of job applications", example = "25")
        long totalApplications,
        @Schema(description = "Applications still awaiting a response", example = "10")
        long waitingResponses,
        @Schema(description = "Applications with an interview scheduled", example = "3")
        long interviewsScheduled,
        @Schema(description = "Applications with overdue follow-up dates", example = "2")
        long overdueFollowUps,
        @Schema(description = "Applications with recruiter DM reminder enabled", example = "5")
        long dmRemindersEnabled,
        @Schema(description = "Applications marked to send later (status is null)", example = "4")
        long toSendLater
) {}
