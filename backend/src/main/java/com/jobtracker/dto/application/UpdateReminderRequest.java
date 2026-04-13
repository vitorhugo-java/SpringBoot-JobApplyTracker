package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to toggle the recruiter DM reminder on an application")
public record UpdateReminderRequest(
        @Schema(description = "Whether the recruiter DM reminder should be enabled", example = "true")
        @NotNull(message = "recruiterDmReminderEnabled is required")
        Boolean recruiterDmReminderEnabled
) {}
