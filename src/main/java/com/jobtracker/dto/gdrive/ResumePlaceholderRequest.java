package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

@Schema(description = "Request for resume placeholder detection or generation")
public record ResumePlaceholderRequest(
        @Schema(description = "Configured base resume identifier")
        @NotNull(message = "baseResumeId is required")
        UUID baseResumeId,

        @Schema(description = "Placeholder values keyed by placeholder name without braces")
        @NotNull(message = "values map is required")
        Map<String, String> values
) {}
