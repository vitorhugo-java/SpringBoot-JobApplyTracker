package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token refresh request. The refresh token is sent automatically via HttpOnly cookie.")
public record RefreshTokenRequest() {}
