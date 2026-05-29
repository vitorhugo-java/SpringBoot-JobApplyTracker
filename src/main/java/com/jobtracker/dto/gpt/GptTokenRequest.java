package com.jobtracker.dto.gpt;

import jakarta.validation.constraints.NotBlank;

public record GptTokenRequest(
        @NotBlank(message = "grant_type is required")
        String grant_type,
        @NotBlank(message = "code is required")
        String code,
        @NotBlank(message = "redirect_uri is required")
        String redirect_uri,
        @NotBlank(message = "code_verifier is required")
        String code_verifier,
        String client_id,
        String client_secret
) {
}
