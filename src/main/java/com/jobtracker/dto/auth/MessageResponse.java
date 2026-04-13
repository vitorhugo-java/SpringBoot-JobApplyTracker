package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generic message response")
public record MessageResponse(
        @Schema(description = "Informational message", example = "Operation completed successfully")
        String message
) {}
