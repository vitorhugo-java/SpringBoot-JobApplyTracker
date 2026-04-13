package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Forgot password request")
public record ForgotPasswordRequest(
        @Schema(description = "Email address associated with the account", example = "john@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email
) {}
