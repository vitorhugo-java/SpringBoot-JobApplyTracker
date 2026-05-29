package com.jobtracker.dto.gpt;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth token response for GPT Actions")
public record GptTokenResponse(
        @Schema(description = "Access token")
        String access_token,
        @Schema(description = "Token type", example = "Bearer")
        String token_type,
        @Schema(description = "Lifetime in seconds", example = "900")
        long expires_in,
        @Schema(description = "Granted scope value")
        String scope
) {
}
