package com.jobtracker.dto.analytics;

import java.util.List;

public record WeeklySummaryResponse(
        int thisWeekApplications,
        int lastWeekApplications,
        int weekOverWeekDelta,
        int thisWeekInterviews,
        int overdueCount,
        List<String> topOrganizationsThisWeek
) {}
