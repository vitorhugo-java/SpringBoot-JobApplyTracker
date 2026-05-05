package com.jobtracker.dto.gamification;

import com.jobtracker.entity.enums.GamificationEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Gamification event emitted by the client")
public record GamificationEventRequest(
        @NotNull
        @Schema(description = "Event type", example = "APPLICATION_CREATED")
        GamificationEventType eventType,
        @Schema(description = "Application identifier related to the event")
        UUID applicationId,
        @Schema(description = "Optional client-side timestamp")
        LocalDateTime occurredAt
) {}
