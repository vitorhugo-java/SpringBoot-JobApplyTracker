package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Authenticated user password change request")
public record ChangePasswordRequest(
        @Schema(description = "Current account password", example = "secureP@ss1")
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @Schema(description = "New account password", example = "newSecureP@ss1")
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword,

        @Schema(description = "Confirm new account password", example = "newSecureP@ss1")
        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}