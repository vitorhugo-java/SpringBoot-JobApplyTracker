package com.jobtracker.controller;

import com.jobtracker.dto.auth.*;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.service.AuthService;
import com.jobtracker.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Authentication and user management endpoints")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthMapper authMapper;
    private final SecurityUtils securityUtils;

    public AuthController(AuthService authService, AuthMapper authMapper, SecurityUtils securityUtils) {
        this.authService = authService;
        this.authMapper = authMapper;
        this.securityUtils = securityUtils;
    }

    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns access and refresh tokens",
        responses = {
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or passwords do not match"),
            @ApiResponse(responseCode = "409", description = "Email already in use")
        }
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
        summary = "Login",
        description = "Authenticates a user and returns access and refresh tokens",
        responses = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
        }
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
        summary = "Refresh access token",
        description = "Issues a new access token using a valid refresh token",
        responses = {
            @ApiResponse(responseCode = "200", description = "Token refreshed",
                content = @Content(schema = @Schema(implementation = RefreshResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
        }
    )
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(
        summary = "Request password reset",
        description = "Sends a password reset token to the provided email address",
        responses = {
            @ApiResponse(responseCode = "200", description = "Reset email sent",
                content = @Content(schema = @Schema(implementation = MessageResponse.class)))
        }
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @Operation(
        summary = "Reset password",
        description = "Resets the user password using a valid reset token",
        responses = {
            @ApiResponse(responseCode = "200", description = "Password reset successful",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset token")
        }
    )
    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @Operation(
        summary = "Logout",
        description = "Invalidates the provided refresh token",
        responses = {
            @ApiResponse(responseCode = "200", description = "Logged out successfully",
                content = @Content(schema = @Schema(implementation = MessageResponse.class)))
        }
    )
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        return ResponseEntity.ok(authService.logout(request));
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
}
