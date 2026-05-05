package com.jobtracker.unit;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.GoogleDriveOAuthState;
import com.jobtracker.entity.User;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.GoogleDriveOAuthService;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleDriveOAuthServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String VALID_STATE = "valid-state-token";
    private static final String AUTH_CODE = "auth-code-123";

    @Mock private GoogleDriveApiClient googleDriveApiClient;
    @Mock private GoogleDriveConnectionRepository connectionRepository;
    @Mock private GoogleDriveOAuthStateRepository oauthStateRepository;
    @Mock private SecurityUtils securityUtils;

    private GoogleDriveProperties googleDriveProperties;
    private GoogleDriveOAuthService oauthService;

    private User user;
    private GoogleDriveOAuthState validOAuthState;

    @BeforeEach
    void setUp() {
        googleDriveProperties = new GoogleDriveProperties(
                "client-id",
                "client-secret",
                "http://localhost:8080/api/v1/google-drive/oauth/callback",
                "http://localhost:5173/settings/google-drive/callback",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token"
        );
        oauthService = new GoogleDriveOAuthService(
                googleDriveApiClient,
                googleDriveProperties,
                connectionRepository,
                oauthStateRepository,
                securityUtils
        );

        user = new User();
        user.setId(USER_ID);
        user.setEmail("user@example.com");

        validOAuthState = new GoogleDriveOAuthState();
        validOAuthState.setUser(user);
        validOAuthState.setStateToken(VALID_STATE);
        validOAuthState.setExpiresAt(LocalDateTime.now().plusMinutes(5));
    }

    @Test
    void handleCallback_shouldRedirectWithError_whenGoogleReturnsError() {
        String result = oauthService.handleCallback(VALID_STATE, null, "access_denied");

        assertThat(result).contains("status=error");
        assertThat(result).contains("Google");
        assertThat(result).contains("OAuth");
        verifyNoInteractions(googleDriveApiClient);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    void handleCallback_shouldRedirectWithError_whenStateIsExpired() {
        validOAuthState.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(oauthStateRepository.findByStateToken(VALID_STATE)).thenReturn(Optional.of(validOAuthState));

        String result = oauthService.handleCallback(VALID_STATE, AUTH_CODE, null);

        assertThat(result).contains("status=error");
        assertThat(result).containsIgnoringCase("expired");
        verifyNoInteractions(googleDriveApiClient);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    void handleCallback_shouldRedirectWithError_whenStateIsInvalid() {
        when(oauthStateRepository.findByStateToken("unknown-state")).thenReturn(Optional.empty());

        String result = oauthService.handleCallback("unknown-state", AUTH_CODE, null);

        assertThat(result).contains("status=error");
        assertThat(result).contains("Invalid");
        verifyNoInteractions(googleDriveApiClient);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    void handleCallback_shouldRedirectWithError_whenMissingState() {
        String result = oauthService.handleCallback(null, AUTH_CODE, null);

        assertThat(result).contains("status=error");
        assertThat(result).contains("Missing");
        verifyNoInteractions(googleDriveApiClient);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    void handleCallback_shouldRedirectWithError_whenMissingCode() {
        String result = oauthService.handleCallback(VALID_STATE, null, null);

        assertThat(result).contains("status=error");
        assertThat(result).contains("Missing");
        verifyNoInteractions(googleDriveApiClient);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    void handleCallback_shouldCreateNewConnection_onFirstConnect() {
        when(oauthStateRepository.findByStateToken(VALID_STATE)).thenReturn(Optional.of(validOAuthState));
        when(googleDriveApiClient.exchangeAuthorizationCode(AUTH_CODE)).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("access-token", "refresh-token",
                        LocalDateTime.now().plusHours(1), "https://www.googleapis.com/auth/drive")
        );
        when(googleDriveApiClient.getCurrentAccount("access-token")).thenReturn(
                new GoogleDriveApiClient.GoogleDriveAccountProfile("perm-1", "user@gmail.com", "User")
        );
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(connectionRepository.save(any(GoogleDriveConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = oauthService.handleCallback(VALID_STATE, AUTH_CODE, null);

        assertThat(result).contains("status=success");
        ArgumentCaptor<GoogleDriveConnection> captor = ArgumentCaptor.forClass(GoogleDriveConnection.class);
        verify(connectionRepository).save(captor.capture());
        GoogleDriveConnection saved = captor.getValue();
        assertThat(saved.getGoogleEmail()).isEqualTo("user@gmail.com");
        assertThat(saved.getRefreshToken()).isEqualTo("refresh-token");
        verify(oauthStateRepository).delete(validOAuthState);
    }

    @Test
    void handleCallback_shouldClearRootFolderAndBaseResumes_whenDifferentAccountConnects() {
        GoogleDriveConnection existingConnection = new GoogleDriveConnection();
        existingConnection.setGoogleAccountId("old-account-id");
        existingConnection.setRootFolderId("old-root-folder");
        existingConnection.setRootFolderName("Old Root");
        existingConnection.setBaseResumes(new ArrayList<>(List.of(new GoogleDriveBaseResume())));

        when(oauthStateRepository.findByStateToken(VALID_STATE)).thenReturn(Optional.of(validOAuthState));
        when(googleDriveApiClient.exchangeAuthorizationCode(AUTH_CODE)).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access", "new-refresh",
                        LocalDateTime.now().plusHours(1), "https://www.googleapis.com/auth/drive")
        );
        when(googleDriveApiClient.getCurrentAccount("new-access")).thenReturn(
                new GoogleDriveApiClient.GoogleDriveAccountProfile("new-account-id", "new@gmail.com", "New User")
        );
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingConnection));
        when(connectionRepository.save(any(GoogleDriveConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = oauthService.handleCallback(VALID_STATE, AUTH_CODE, null);

        assertThat(result).contains("status=success");
        ArgumentCaptor<GoogleDriveConnection> captor = ArgumentCaptor.forClass(GoogleDriveConnection.class);
        verify(connectionRepository).save(captor.capture());
        GoogleDriveConnection saved = captor.getValue();
        assertThat(saved.getRootFolderId()).isNull();
        assertThat(saved.getRootFolderName()).isNull();
        assertThat(saved.getBaseResumes()).isEmpty();
        assertThat(saved.getGoogleEmail()).isEqualTo("new@gmail.com");
    }

    @Test
    void handleCallback_shouldPreserveRootFolder_whenSameAccountReconnects() {
        GoogleDriveConnection existingConnection = new GoogleDriveConnection();
        existingConnection.setGoogleAccountId("same-account-id");
        existingConnection.setRootFolderId("existing-root-folder");
        existingConnection.setRootFolderName("Job Tracker Root");
        existingConnection.setBaseResumes(new ArrayList<>());

        when(oauthStateRepository.findByStateToken(VALID_STATE)).thenReturn(Optional.of(validOAuthState));
        when(googleDriveApiClient.exchangeAuthorizationCode(AUTH_CODE)).thenReturn(
                new GoogleDriveApiClient.OAuthTokens("new-access", "new-refresh",
                        LocalDateTime.now().plusHours(1), "https://www.googleapis.com/auth/drive")
        );
        when(googleDriveApiClient.getCurrentAccount("new-access")).thenReturn(
                new GoogleDriveApiClient.GoogleDriveAccountProfile("same-account-id", "user@gmail.com", "User")
        );
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existingConnection));
        when(connectionRepository.save(any(GoogleDriveConnection.class))).thenAnswer(inv -> inv.getArgument(0));

        String result = oauthService.handleCallback(VALID_STATE, AUTH_CODE, null);

        assertThat(result).contains("status=success");
        ArgumentCaptor<GoogleDriveConnection> captor = ArgumentCaptor.forClass(GoogleDriveConnection.class);
        verify(connectionRepository).save(captor.capture());
        GoogleDriveConnection saved = captor.getValue();
        assertThat(saved.getRootFolderId()).isEqualTo("existing-root-folder");
    }

    @Test
    void handleCallback_shouldDeleteStateEvenOnError() {
        validOAuthState.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(oauthStateRepository.findByStateToken(VALID_STATE)).thenReturn(Optional.of(validOAuthState));

        oauthService.handleCallback(VALID_STATE, AUTH_CODE, null);

        verify(oauthStateRepository).delete(validOAuthState);
    }

    @Test
    void disconnect_shouldDeleteExistingConnection() {
        GoogleDriveConnection connection = new GoogleDriveConnection();
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));

        oauthService.disconnect();

        verify(connectionRepository).delete(connection);
    }

    @Test
    void disconnect_shouldSucceed_whenNoConnectionExists() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        oauthService.disconnect();

        verify(connectionRepository, never()).delete(any());
    }
}
