package com.jobtracker.unit;

import com.jobtracker.entity.RefreshToken;
import com.jobtracker.entity.User;
import com.jobtracker.exception.UnauthorizedException;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpirationMs", 604800000L);
    }

    @Test
    void createRefreshToken_shouldPersistAndReturnToken() {
        User user = buildUser(1L, "test@example.com");
        RefreshToken saved = buildRefreshToken(user, false, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(saved);

        RefreshToken result = refreshTokenService.createRefreshToken(user);

        assertThat(result).isNotNull();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void verifyAndRotate_shouldRevokeOldAndCreateNew() {
        User user = buildUser(1L, "test@example.com");
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken existing = buildRefreshToken(user, false, LocalDateTime.now().plusDays(7));
        existing.setToken(tokenValue);

        RefreshToken newToken = buildRefreshToken(user, false, LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(existing).thenReturn(newToken);

        RefreshToken result = refreshTokenService.verifyAndRotate(tokenValue);

        assertThat(result).isNotNull();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).isRevoked()).isTrue();
    }

    @Test
    void verifyAndRotate_shouldThrowWhenTokenNotFound() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> refreshTokenService.verifyAndRotate("nonexistent"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void verifyAndRotate_shouldThrowWhenTokenRevoked() {
        User user = buildUser(1L, "test@example.com");
        RefreshToken revoked = buildRefreshToken(user, true, LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.verifyAndRotate("revokedToken"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void verifyAndRotate_shouldThrowWhenTokenExpired() {
        User user = buildUser(1L, "test@example.com");
        RefreshToken expired = buildRefreshToken(user, false, LocalDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.verifyAndRotate("expiredToken"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void revokeToken_shouldMarkTokenAsRevoked() {
        User user = buildUser(1L, "test@example.com");
        String tokenValue = UUID.randomUUID().toString();
        RefreshToken token = buildRefreshToken(user, false, LocalDateTime.now().plusDays(7));
        token.setToken(tokenValue);
        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any())).thenReturn(token);

        refreshTokenService.revokeToken(tokenValue);

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeToken_shouldDoNothingWhenTokenNotFound() {
        when(refreshTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());
        refreshTokenService.revokeToken("ghost-token");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeAllByUserId_shouldDelegateToRepository() {
        refreshTokenService.revokeAllByUserId(1L);
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    private User buildUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setName("Test User");
        user.setEmail(email);
        user.setPasswordHash("hashed");
        return user;
    }

    private RefreshToken buildRefreshToken(User user, boolean revoked, LocalDateTime expiry) {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setRevoked(revoked);
        token.setExpiryDate(expiry);
        return token;
    }
}
