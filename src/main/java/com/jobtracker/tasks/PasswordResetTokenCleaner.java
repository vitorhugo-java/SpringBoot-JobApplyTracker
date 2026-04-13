package com.jobtracker.tasks;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.jobtracker.service.PasswordResetService;

@Component
public class PasswordResetTokenCleaner {
    private final PasswordResetService passwordResetService;

    public PasswordResetTokenCleaner(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanExpiredTokens() {
        passwordResetService.cleanExpiredTokens();
    }
}
