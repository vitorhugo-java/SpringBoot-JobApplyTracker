package com.jobtracker.service;

import com.jobtracker.config.JwtService;
import com.jobtracker.dto.auth.*;
import com.jobtracker.entity.PasswordResetToken;
import com.jobtracker.entity.RefreshToken;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ConflictException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.repository.UserRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final AuthMapper authMapper;
    private final Tracer tracer;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       PasswordResetService passwordResetService,
                       AuthMapper authMapper,
                       Tracer tracer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.authMapper = authMapper;
        this.tracer = tracer;
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

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Span span = tracer.nextSpan().name("login").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid credentials");
            }

            return buildAuthResponse(user);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional
    public RefreshResponse refresh(RefreshTokenRequest request) {
        Span span = tracer.nextSpan().name("token-refresh").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            RefreshToken newRefreshToken = refreshTokenService.verifyAndRotate(request.refreshToken());
            User user = newRefreshToken.getUser();

            UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                    user.getEmail(), user.getPasswordHash(), Collections.emptyList());
            String accessToken = jwtService.generateToken(userDetails);

            return new RefreshResponse(accessToken, newRefreshToken.getToken());
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            PasswordResetToken token = passwordResetService.createResetToken(user);
            // In a real application, you would send an email with the reset token
            // For now, we log it (do not expose token in response for security)
        });
        // Always return success to prevent email enumeration
        return new MessageResponse("If an account with that email exists, a password reset link has been sent");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        PasswordResetToken resetToken = passwordResetService.verifyToken(request.token());
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        passwordResetService.markTokenAsUsed(resetToken);
        // Revoke all existing refresh tokens for security
        refreshTokenService.revokeAllByUserId(user.getId());

        return new MessageResponse("Password has been reset successfully");
    }

    @Transactional
    public MessageResponse logout(LogoutRequest request) {
        refreshTokenService.revokeToken(request.refreshToken());
        return new MessageResponse("Logged out successfully");
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPasswordHash(), Collections.emptyList());
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken(), authMapper.toUserResponse(user));
    }
}
