package com.jobtracker.service;

import com.jobtracker.entity.PasswordResetToken;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.repository.PasswordResetTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final long TOKEN_EXPIRY_MINUTES = 30;

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    public PasswordResetToken createResetToken(User user) {
        // Invalidate any existing tokens
        passwordResetTokenRepository.invalidateAllByUserId(user.getId());

        PasswordResetToken token = new PasswordResetToken();
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES));
        token.setUsed(false);
        token.setUser(user);
        return passwordResetTokenRepository.save(token);
    }

    @Transactional
    public PasswordResetToken verifyToken(String tokenValue) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (token.isUsed()) {
            throw new BadRequestException("Password reset token has already been used");
        }

        if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Password reset token has expired");
        }

        return token;
    }

    @Transactional
    public void markTokenAsUsed(PasswordResetToken token) {
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }
}
