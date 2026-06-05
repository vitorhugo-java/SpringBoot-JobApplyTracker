package com.jobtracker.dto.dashboard;

import jakarta.validation.constraints.Min;

public record UpdateInterviewCountRequest(
        @Min(0) long count
) {}
