package com.jobtracker.dto.auth;

public record UserResponse(
        Long id,
        String name,
        String email
) {}
