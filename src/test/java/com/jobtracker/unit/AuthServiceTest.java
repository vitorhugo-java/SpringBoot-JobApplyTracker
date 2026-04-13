package com.jobtracker.unit;

import com.jobtracker.config.JwtService;
import com.jobtracker.dto.auth.*;
import com.jobtracker.entity.PasswordResetToken;
import com.jobtracker.entity.RefreshToken;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ConflictException;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.service.AuthService;
import com.jobtracker.service.PasswordResetService;
import com.jobtracker.service.RefreshTokenService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private AuthMapper authMapper;
    @Mock(answer = RETURNS_DEEP_STUBS) private Tracer tracer;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldReturnAuthResponse_whenValidRequest() {
        RegisterRequest request = new RegisterRequest("John", "john@example.com", "pass1234", "pass1234");
        User savedUser = buildUser(1L, "john@example.com");

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn(buildRefreshToken(savedUser));
        when(authMapper.toUserResponse(savedUser)).thenReturn(new UserResponse(1L, "John", "john@example.com"));

        AuthResponse result = authService.register(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.user().email()).isEqualTo("john@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrow_whenPasswordsDoNotMatch() {
        RegisterRequest request = new RegisterRequest("John", "john@example.com", "pass1234", "different");
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Passwords do not match");
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("John", "john@example.com", "pass1234", "pass1234");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void login_shouldReturnAuthResponse_whenValidCredentials() {
        LoginRequest request = new LoginRequest("john@example.com", "pass1234");
        User user = buildUser(1L, "john@example.com");

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(buildRefreshToken(user));
        when(authMapper.toUserResponse(user)).thenReturn(new UserResponse(1L, "John", "john@example.com"));

        AuthResponse result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void login_shouldThrow_whenUserNotFound() {
        LoginRequest request = new LoginRequest("nobody@example.com", "pass1234");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_shouldThrow_whenPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("john@example.com", "wrongpass");
        User user = buildUser(1L, "john@example.com");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void refresh_shouldReturnNewTokens() {
        User user = buildUser(1L, "john@example.com");
        RefreshToken newToken = buildRefreshToken(user);
        when(refreshTokenService.verifyAndRotate(anyString())).thenReturn(newToken);
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("new-access-token");

        RefreshResponse result = authService.refresh(new RefreshTokenRequest("old-token"));

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isNotNull();
    }

    @Test
    void logout_shouldRevokeRefreshToken() {
        MessageResponse result = authService.logout(new LogoutRequest("some-token"));
        verify(refreshTokenService).revokeToken("some-token");
        assertThat(result.message()).contains("Logged out");
    }

    @Test
    void forgotPassword_shouldAlwaysReturnSuccess() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("anyone@example.com");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        MessageResponse result = authService.forgotPassword(request);
        assertThat(result.message()).isNotNull();
    }

    @Test
    void resetPassword_shouldThrow_whenPasswordsDoNotMatch() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "pass1234", "different");
        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Passwords do not match");
    }

    @Test
    void resetPassword_shouldSucceed_whenValidRequest() {
        User user = buildUser(1L, "john@example.com");
        PasswordResetToken resetToken = buildPasswordResetToken(user);
        when(passwordResetService.verifyToken("valid-token")).thenReturn(resetToken);
        when(passwordEncoder.encode(anyString())).thenReturn("newhash");
        when(userRepository.save(any(User.class))).thenReturn(user);

        ResetPasswordRequest request = new ResetPasswordRequest("valid-token", "newpass1", "newpass1");
        MessageResponse result = authService.resetPassword(request);

        assertThat(result.message()).contains("reset successfully");
        verify(passwordResetService).markTokenAsUsed(resetToken);
        verify(refreshTokenService).revokeAllByUserId(1L);
    }

    private User buildUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName("John");
        user.setEmail(email);
        user.setPasswordHash("hashed");
        return user;
    }

    private RefreshToken buildRefreshToken(User user) {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setRevoked(false);
        token.setExpiryDate(LocalDateTime.now().plusDays(7));
        return token;
    }

    private PasswordResetToken buildPasswordResetToken(User user) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setId(1L);
        prt.setToken("valid-token");
        prt.setUser(user);
        prt.setUsed(false);
        prt.setExpiryDate(LocalDateTime.now().plusHours(1));
        return prt;
    }
}
