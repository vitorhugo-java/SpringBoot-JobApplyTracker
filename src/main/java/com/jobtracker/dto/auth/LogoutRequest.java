package com.jobtracker.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Logout request. The refresh token is invalidated from the HttpOnly cookie automatically.")
public record LogoutRequest() {}
