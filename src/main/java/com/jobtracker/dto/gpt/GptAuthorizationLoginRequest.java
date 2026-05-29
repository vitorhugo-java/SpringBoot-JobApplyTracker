package com.jobtracker.dto.gpt;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record GptAuthorizationLoginRequest(
        @NotBlank(message = "response_type is required")
        String response_type,
        @NotBlank(message = "client_id is required")
        String client_id,
        @NotBlank(message = "redirect_uri is required")
        String redirect_uri,
        String scope,
        String state,
        @NotBlank(message = "code_challenge is required")
        String code_challenge,
        @NotBlank(message = "code_challenge_method is required")
        String code_challenge_method,
        @Email(message = "email must be valid")
        @NotBlank(message = "email is required")
        String email,
        @NotBlank(message = "password is required")
        String password,
        String approve
) {
    public boolean approved() {
        return approve != null && !approve.isBlank();
    }
}
