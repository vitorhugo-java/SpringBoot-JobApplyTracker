package com.jobtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.service.GoogleDriveApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleDriveControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private UserGamificationRepository userGamificationRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private GoogleDriveBaseResumeRepository googleDriveBaseResumeRepository;
    @Autowired private GoogleDriveOAuthStateRepository googleDriveOAuthStateRepository;
    @Autowired private FakeGoogleDriveApiClient googleDriveApiClient;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        googleDriveOAuthStateRepository.deleteAll();
        googleDriveBaseResumeRepository.deleteAll();
        googleDriveConnectionRepository.deleteAll();
        userAchievementRepository.deleteAll();
        userGamificationRepository.deleteAll();
        applicationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest("Drive User", "driveuser@example.com", "pass1234", "pass1234");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
    }

    @Test
    void startOauth_shouldReturnAuthorizationUrl() throws Exception {
        mockMvc.perform(post("/api/v1/google-drive/oauth/start")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andExpect(jsonPath("$.redirectUri").value("http://localhost:8080/api/v1/google-drive/oauth/callback"))
                .andExpect(jsonPath("$.scopes[0]").value("https://www.googleapis.com/auth/drive"));
    }

    @Test
    void oauthCallback_shouldPersistConnectionAndRedirectToFrontend() throws Exception {
        googleDriveApiClient.tokens = new GoogleDriveApiClient.OAuthTokens(
                "drive-access",
                "drive-refresh",
                LocalDateTime.now().plusHours(1),
                "https://www.googleapis.com/auth/drive"
        );
        googleDriveApiClient.accountProfile =
                new GoogleDriveApiClient.GoogleDriveAccountProfile("perm-123", "connected@example.com", "Drive User");

        MvcResult startResult = mockMvc.perform(post("/api/v1/google-drive/oauth/start")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode startJson = objectMapper.readTree(startResult.getResponse().getContentAsString());
        String state = startJson.get("state").asText();

        mockMvc.perform(get("/api/v1/google-drive/oauth/callback")
                        .param("state", state)
                        .param("code", "auth-code"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("status=success")));

        GoogleDriveConnection connection = googleDriveConnectionRepository.findAll().getFirst();
        assertThat(connection.getGoogleEmail()).isEqualTo("connected@example.com");
        assertThat(connection.getRefreshToken()).isEqualTo("drive-refresh");
    }

    @Test
    void updateRootFolder_shouldReturnUpdatedStatus() throws Exception {
        googleDriveConnectionRepository.save(buildConnection());
        googleDriveApiClient.fileMetadataById.put("folder-123",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "folder-123",
                        "Root Folder",
                        GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/folder-123"
                ));

        mockMvc.perform(put("/api/v1/google-drive/root-folder")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderIdOrUrl\":\"folder-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.rootFolderId").value("folder-123"))
                .andExpect(jsonPath("$.rootFolderName").value("Root Folder"));
    }

    private GoogleDriveConnection buildConnection() {
        GoogleDriveConnection connection = new GoogleDriveConnection();
        connection.setUser(userRepository.findByEmail("driveuser@example.com").orElseThrow());
        connection.setGoogleAccountId("perm-123");
        connection.setGoogleEmail("connected@example.com");
        connection.setGoogleDisplayName("Drive User");
        connection.setAccessToken("drive-access");
        connection.setRefreshToken("drive-refresh");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setGrantedScopes("https://www.googleapis.com/auth/drive");
        connection.setConnectedAt(LocalDateTime.now());
        return connection;
    }

    @TestConfiguration
    static class GoogleDriveTestConfig {
        @Bean
        @Primary
        FakeGoogleDriveApiClient googleDriveApiClient() {
            return new FakeGoogleDriveApiClient();
        }
    }

    static class FakeGoogleDriveApiClient implements GoogleDriveApiClient {
        private OAuthTokens tokens = new OAuthTokens(
                "drive-access",
                "drive-refresh",
                LocalDateTime.now().plusHours(1),
                "https://www.googleapis.com/auth/drive"
        );
        private GoogleDriveAccountProfile accountProfile =
                new GoogleDriveAccountProfile("perm-123", "connected@example.com", "Drive User");
        private final Map<String, DriveFileMetadata> fileMetadataById = new HashMap<>();

        @Override
        public String buildAuthorizationUrl(String state) {
            return "https://accounts.google.com/o/oauth2/v2/auth?state=" + state;
        }

        @Override
        public OAuthTokens exchangeAuthorizationCode(String code) {
            return tokens;
        }

        @Override
        public OAuthTokens refreshAccessToken(String refreshToken) {
            return tokens;
        }

        @Override
        public GoogleDriveAccountProfile getCurrentAccount(String accessToken) {
            return accountProfile;
        }

        @Override
        public DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
            return fileMetadataById.get(fileId);
        }

        @Override
        public Optional<DriveFileMetadata> findFolderByName(String accessToken, String parentFolderId, String folderName) {
            return Optional.empty();
        }

        @Override
        public DriveFileMetadata createFolder(String accessToken, String parentFolderId, String folderName) {
            return new DriveFileMetadata("created-folder", folderName, GOOGLE_FOLDER_MIME_TYPE, null);
        }

        @Override
        public DriveFileMetadata copyGoogleDoc(String accessToken, String sourceFileId, String targetFolderId, String newName) {
            return new DriveFileMetadata("copied-file", newName, GOOGLE_DOC_MIME_TYPE, null);
        }
    }
}
