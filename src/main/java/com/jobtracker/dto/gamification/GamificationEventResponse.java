package com.jobtracker.dto.gamification;

import com.jobtracker.entity.enums.GamificationEventType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of applying a gamification event")
public record GamificationEventResponse(
        @Schema(description = "Processed event type", example = "APPLICATION_CREATED")
        GamificationEventType eventType,
        @Schema(description = "XP awarded for the event", example = "10")
        int xpAwarded,
        @Schema(description = "Whether the event caused a level up")
        boolean leveledUp,
        @Schema(description = "Human-readable feedback message")
        String message,
        @Schema(description = "Updated profile snapshot")
        GamificationProfileResponse profile
) {}
