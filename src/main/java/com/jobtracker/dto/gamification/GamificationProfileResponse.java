package com.jobtracker.dto.gamification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Gamification profile snapshot for the authenticated user")
public record GamificationProfileResponse(
        @Schema(description = "Total accumulated XP", example = "75")
        long currentXp,
        @Schema(description = "Current level", example = "1")
        int level,
        @Schema(description = "XP floor of the current level", example = "0")
        long currentLevelXp,
        @Schema(description = "XP required to reach the next level", example = "100")
        long nextLevelXp,
        @Schema(description = "Remaining XP to level up", example = "25")
        long xpToNextLevel,
        @Schema(description = "Progress within the current level", example = "75")
        int progressPercentage,
        @Schema(description = "Localized rank title", example = "Desempregado de Aluguel")
        String rankTitle,
        @Schema(description = "Current application streak in days", example = "0")
        int streakDays
) {}
