package com.jobtracker.controller;

import com.jobtracker.dto.auth.*;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.service.AuthService;
import com.jobtracker.util.SecurityUtils;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication and user management endpoints")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthMapper authMapper;
    private final SecurityUtils securityUtils;

    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth/refresh";

    public AuthController(AuthService authService, AuthMapper authMapper, SecurityUtils securityUtils) {
        this.authService = authService;
        this.authMapper = authMapper;
        this.securityUtils = securityUtils;
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        String cookieValue = String.format("refreshToken=%s; Path=%s; HttpOnly; Secure; SameSite=Lax", refreshToken, REFRESH_COOKIE_PATH);
        response.addHeader("Set-Cookie", cookieValue);
    }

    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns access and refresh tokens",
        responses = {
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or passwords do not match"),
            @ApiResponse(responseCode = "409", description = "Email already in use"),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    @PostMapping("/register")
    @RateLimiter(name = "authRegister")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        setRefreshTokenCookie(response, authService.getLastRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @Operation(
        summary = "Login",
        description = "Authenticates a user and returns access token. Refresh token is sent via HttpOnly cookie.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    @PostMapping("/login")
    @RateLimiter(name = "authLogin")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authService.getLastRefreshToken());
        return ResponseEntity.ok(authResponse);
    }

    @Operation(
        summary = "Refresh access token",
        description = "Issues a new access token using the refresh token from HttpOnly cookie",
        responses = {
            @ApiResponse(responseCode = "200", description = "Token refreshed",
                content = @Content(schema = @Schema(implementation = RefreshResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    @PostMapping("/refresh")
    @RateLimiter(name = "authRefresh")
    public ResponseEntity<RefreshResponse> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken, 
                                                   HttpServletResponse response) {
        RefreshTokenRequest request = new RefreshTokenRequest();
        RefreshResponse refreshResponse = authService.refresh(request, refreshToken);
        setRefreshTokenCookie(response, authService.getLastRefreshToken());
        return ResponseEntity.ok(refreshResponse);
    }

    @Operation(
        summary = "Request password reset",
        description = "Sends a password reset token to the provided email address",
        responses = {
            @ApiResponse(responseCode = "200", description = "Reset email sent",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    @PostMapping("/forgot-password")
    @RateLimiter(name = "authForgotPassword")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @Operation(
        summary = "Reset password",
        description = "Resets the user password using a valid reset token",
        responses = {
            @ApiResponse(responseCode = "200", description = "Password reset successful",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset token"),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    @PostMapping("/reset-password")
    @RateLimiter(name = "authResetPassword")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @Operation(
        summary = "Logout",
        description = "Invalidates the refresh token from HttpOnly cookie",
        responses = {
            @ApiResponse(responseCode = "200", description = "Logged out successfully",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many requests")
        }
    )
    @PostMapping("/logout")
    @RateLimiter(name = "authLogout")
    public ResponseEntity<MessageResponse> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken, 
                                                  HttpServletResponse response) {
        LogoutRequest request = new LogoutRequest();
        MessageResponse result = authService.logout(request, refreshToken);
        // Clear the refresh token cookie
        String clearCookie = "refreshToken=; Path=" + REFRESH_COOKIE_PATH + "; HttpOnly; Secure; SameSite=Lax; Max-Age=0";
        response.addHeader("Set-Cookie", clearCookie);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Get current user",
        description = "Returns the currently authenticated user's profile",
        responses = {
            @ApiResponse(responseCode = "200", description = "Current user details",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(authMapper.toUserResponse(securityUtils.getCurrentUser()));
    }

    @Operation(
        summary = "Update current user profile",
        description = "Updates the currently authenticated user's profile data",
        responses = {
            @ApiResponse(responseCode = "200", description = "Profile updated",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(request));
    }

    @Operation(
        summary = "Change current user password",
        description = "Changes the currently authenticated user's password",
        responses = {
            @ApiResponse(responseCode = "200", description = "Password changed",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or wrong current password"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @PutMapping("/me/password")
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(request));
    }

}
