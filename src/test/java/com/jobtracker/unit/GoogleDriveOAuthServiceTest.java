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
    private static final String TEST_SCOPES = "https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/documents.readonly";

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
                "https://oauth2.googleapis.com/token",
                TEST_SCOPES
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
}