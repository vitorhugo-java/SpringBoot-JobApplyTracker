package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Password reset request")
public record ResetPasswordRequest(
        @Schema(description = "Password reset token received via email", example = "abc123xyz")
        @NotBlank(message = "Token is required")
        String token,

        @Schema(description = "New password (minimum 8 characters)", example = "newSecureP@ss1")
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword,

        @Schema(description = "Confirm new password (must match newPassword)", example = "newSecureP@ss1")
        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}
