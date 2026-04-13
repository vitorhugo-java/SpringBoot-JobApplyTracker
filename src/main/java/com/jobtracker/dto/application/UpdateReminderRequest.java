package com.jobtracker.dto.application;

import jakarta.validation.constraints.NotNull;

public record UpdateReminderRequest(
        @NotNull(message = "recruiterDmReminderEnabled is required")
        Boolean recruiterDmReminderEnabled
) {}
