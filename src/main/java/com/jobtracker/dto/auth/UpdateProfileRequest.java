package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Schema(description = "Authenticated user profile update request")
public record UpdateProfileRequest(
        @Schema(description = "User display name", example = "John Doe")
        @NotBlank(message = "Name is required")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @Schema(description = "Preferred daily reminder time", example = "19:00:00")
        LocalTime reminderTime
) {}