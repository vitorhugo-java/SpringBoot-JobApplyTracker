package com.jobtracker.dto.analytics;

import java.time.LocalDate;
import java.util.List;

public record OrganizationSummary(
        String organization,
        int totalApplications,
        List<String> statuses,
        boolean hasInterview,
        LocalDate lastApplicationDate
) {}
