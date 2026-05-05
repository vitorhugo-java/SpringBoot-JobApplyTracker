package com.jobtracker.dto.gamification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Achievement status for the authenticated user")
public record AchievementResponse(
        @Schema(description = "Stable achievement code", example = "EARLY_BIRD")
        String code,
        @Schema(description = "Display name", example = "Early Bird")
        String name,
        @Schema(description = "Achievement description")
        String description,
        @Schema(description = "Icon token for the UI", example = "sunrise")
        String icon,
        @Schema(description = "Whether the achievement was unlocked")
        boolean unlocked,
        @Schema(description = "Unlock timestamp when available")
        LocalDateTime achievedAt
) {}
