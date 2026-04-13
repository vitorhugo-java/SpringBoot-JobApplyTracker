package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Authenticated user profile")
public record UserResponse(
        @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
        @Schema(description = "User display name", example = "John Doe")
        String name,
        @Schema(description = "User email address", example = "john@example.com")
        String email
) {}
