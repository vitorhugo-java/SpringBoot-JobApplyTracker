package com.jobtracker.service;

import com.jobtracker.entity.PasswordResetToken;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.security.SecureRandom;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_RANDOM_BYTES = 48;

    private final SecureRandom secureRandom = new SecureRandom();

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final String resetPasswordBaseUrl;
    private final long tokenExpiryHours;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService,
                                EmailService emailService,
                                @Value("${app.frontend.reset-password-url:https://your-frontend.com/reset-password}") String resetPasswordBaseUrl,
                                @Value("${app.security.password-reset-token-expiration-hours:24}") long tokenExpiryHours) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
        this.resetPasswordBaseUrl = resetPasswordBaseUrl;
        this.tokenExpiryHours = tokenExpiryHours;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();
        PasswordResetToken token = createResetToken(user);
        String resetLink = buildResetLink(token.getToken());
        emailService.sendPasswordResetEmail(user, resetLink, token.getExpiryDate());
        log.info("event=PASSWORD_RESET_REQUESTED userId={}", user.getId());
    }

    @Transactional
    public PasswordResetToken createResetToken(User user) {
        // Invalidate any existing tokens
        passwordResetTokenRepository.invalidateAllByUserId(user.getId());

        PasswordResetToken token = new PasswordResetToken();
        token.setToken(generateSecureToken());
        token.setExpiryDate(LocalDateTime.now().plusHours(tokenExpiryHours));
        token.setUsed(false);
        token.setUser(user);
        return passwordResetTokenRepository.save(token);
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken resetToken = verifyToken(tokenValue);
        User user = resetToken.getUser();

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        markTokenAsUsed(resetToken);
        refreshTokenService.revokeAllByUserId(user.getId());
        log.info("event=PASSWORD_RESET_SUCCESS userId={}", user.getId());
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

    @Transactional
    public void cleanExpiredTokens() {
        passwordResetTokenRepository.deleteAllByExpiryDateBefore(LocalDateTime.now());
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String buildResetLink(String tokenValue) {
        String separator = resetPasswordBaseUrl.contains("?") ? "&" : "?";
        return resetPasswordBaseUrl + separator + "token=" + tokenValue;
    }
}
