package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Logout request")
public record LogoutRequest(
        @Schema(description = "Refresh token to be invalidated", example = "dGhpcyBpcyBhIHJlZnJlc2g...")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
