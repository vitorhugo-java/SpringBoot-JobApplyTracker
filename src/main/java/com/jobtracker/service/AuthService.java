package com.jobtracker.service;

import com.jobtracker.config.JwtService;
import com.jobtracker.dto.auth.*;
import com.jobtracker.entity.RefreshToken;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ConflictException;
import com.jobtracker.exception.UnauthorizedException;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final ThreadLocal<String> lastRefreshToken = new ThreadLocal<>();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final AuthMapper authMapper;
    private final Tracer tracer;
    private final SecurityUtils securityUtils;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       PasswordResetService passwordResetService,
                       AuthMapper authMapper,
                       Tracer tracer,
                       SecurityUtils securityUtils) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.authMapper = authMapper;
        this.tracer = tracer;
        this.securityUtils = securityUtils;
    }

    /**
     * Retrieve the last generated refresh token. Used by the controller to set the cookie.
     * This value is stored in ThreadLocal and should be cleared after use.
     */
    public String getLastRefreshToken() {
        try {
            return lastRefreshToken.get();
        } finally {
            lastRefreshToken.remove();
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user = userRepository.save(user);
        log.info("event=REGISTRATION_SUCCESS email={} userId={}", user.getEmail(), user.getId());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Span span = tracer.nextSpan().name("login").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> {
                        log.warn("event=LOGIN_FAILURE reason=USER_NOT_FOUND email={}", request.email());
                        return new BadCredentialsException("Invalid credentials");
                    });

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                log.warn("event=LOGIN_FAILURE reason=WRONG_PASSWORD userId={}", user.getId());
                throw new BadCredentialsException("Invalid credentials");
            }

            log.info("event=LOGIN_SUCCESS userId={}", user.getId());
            return buildAuthResponse(user);
        } catch (BadCredentialsException e) {
            // Expected auth failure – do not mark as span error
            throw e;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional
    public RefreshResponse refresh(RefreshTokenRequest request, String refreshToken) {
        Span span = tracer.nextSpan().name("token-refresh").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new UnauthorizedException("Refresh token is required");
            }
            
            RefreshToken newRefreshToken = refreshTokenService.verifyAndRotate(refreshToken);
            User user = newRefreshToken.getUser();

            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                    user.getEmail(), user.getPasswordHash(), Collections.emptyList());
            String accessToken = jwtService.generateToken(userDetails);

            // Store the new refresh token for the controller to set in the cookie
            lastRefreshToken.set(newRefreshToken.getToken());

            return new RefreshResponse(accessToken);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        // Always return success to prevent email enumeration
        return new MessageResponse("If an account with that email exists, a password reset link has been sent");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        passwordResetService.resetPassword(request.token(), request.newPassword());

        return new MessageResponse("Password has been reset successfully");
    }

    @Transactional
    public MessageResponse logout(LogoutRequest request, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revokeToken(refreshToken);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userEmail = (auth != null && auth.isAuthenticated()) ? auth.getName() : "unknown";
        log.info("event=LOGOUT_SUCCESS userEmail={}", userEmail);
        return new MessageResponse("Logged out successfully");
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = securityUtils.getCurrentUser();
        user.setName(request.name().trim());
        user = userRepository.save(user);
        return authMapper.toUserResponse(user);
    }

    @Transactional
    public MessageResponse changePassword(ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        User user = securityUtils.getCurrentUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllByUserId(user.getId());

        return new MessageResponse("Password updated successfully");
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPasswordHash(), Collections.emptyList());
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Store the refresh token for the controller to set in the cookie
        lastRefreshToken.set(refreshToken.getToken());

        return new AuthResponse(accessToken, authMapper.toUserResponse(user));
    }
}
