package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authenticated user profile")
public record UserResponse(
        @Schema(description = "User ID", example = "1")
        Long id,
        @Schema(description = "User display name", example = "John Doe")
        String name,
        @Schema(description = "User email address", example = "john@example.com")
        String email
) {}
