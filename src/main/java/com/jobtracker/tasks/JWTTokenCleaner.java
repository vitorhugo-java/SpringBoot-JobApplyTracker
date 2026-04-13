package com.jobtracker.tasks;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobtracker.service.RefreshTokenService;

@Component
public class JWTTokenCleaner {
    private final RefreshTokenService refreshTokenService;

    public JWTTokenCleaner(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanExpiredTokens() {
        refreshTokenService.cleanExpiredTokens();
    }
}
