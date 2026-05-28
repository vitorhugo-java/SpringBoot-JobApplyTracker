package com.jobtracker.unit;

import com.jobtracker.config.GoogleDriveClientConfig;
import com.jobtracker.config.GoogleDriveProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDriveClientConfigTest {

    private static final String TEST_SCOPES = "https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/documents.readonly";

    private final GoogleDriveClientConfig config = new GoogleDriveClientConfig();

    @Test
    void googleDriveClientRegistration_shouldUseConfiguredValues_whenCredentialsExist() {
        GoogleDriveProperties properties = new GoogleDriveProperties(
                "client-id",
                "client-secret",
                "http://localhost:8080/api/v1/google-drive/oauth/callback",
                "http://localhost:5173/settings/google-drive/callback",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                TEST_SCOPES
        );

        ClientRegistration registration = config.googleDriveClientRegistration(properties);

        assertThat(registration.getClientId()).isEqualTo("client-id");
        assertThat(registration.getClientSecret()).isEqualTo("client-secret");
        assertThat(registration.getRedirectUri()).isEqualTo("http://localhost:8080/api/v1/google-drive/oauth/callback");
    }

    @Test
    void googleDriveClientRegistration_shouldUseSafePlaceholders_whenCredentialsAreMissing() {
        GoogleDriveProperties properties = new GoogleDriveProperties(
                "",
                "",
                "http://localhost:8080/api/v1/google-drive/oauth/callback",
                "",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                TEST_SCOPES
        );

        ClientRegistration registration = config.googleDriveClientRegistration(properties);

        assertThat(properties.isConfigured()).isFalse();
        assertThat(registration.getClientId()).isEqualTo("google-drive-disabled-client");
        assertThat(registration.getClientSecret()).isEqualTo("google-drive-disabled-secret");
        assertThat(registration.getRedirectUri()).isEqualTo("http://localhost/google-drive-disabled");
    }
}