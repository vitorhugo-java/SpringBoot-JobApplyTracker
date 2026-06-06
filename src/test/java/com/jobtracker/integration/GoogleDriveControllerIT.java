package com.jobtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.Role;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.RoleName;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.RoleRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    @Autowired private InterviewEventRepository interviewEventRepository;
    @Autowired private UserInterviewMetricsRepository userInterviewMetricsRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private GoogleDriveBaseResumeRepository googleDriveBaseResumeRepository;
    @Autowired private GoogleDriveOAuthStateRepository googleDriveOAuthStateRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private FakeGoogleDriveApiClient googleDriveApiClient;

    private String betaAccessToken;
    private String nonBetaAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        googleDriveApiClient.reset();
        googleDriveOAuthStateRepository.deleteAll();
        googleDriveBaseResumeRepository.deleteAll();
        googleDriveConnectionRepository.deleteAll();
        userAchievementRepository.deleteAll();
        userGamificationRepository.deleteAll();
        interviewEventRepository.deleteAll();
        applicationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userInterviewMetricsRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest("Drive User", "driveuser@example.com", "pass1234", "pass1234");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        nonBetaAccessToken = auth.accessToken();

        User user = userRepository.findByEmail("driveuser@example.com").orElseThrow();
        Role betaRole = roleRepository.findByName(RoleName.BETA)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(RoleName.BETA);
                    return roleRepository.save(role);
                });
        user.setRoles(Set.of(
                user.getRoles().stream().findFirst().orElseThrow(),
                betaRole));
        userRepository.save(user);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driveuser@example.com",
                                  "password": "pass1234"
                                }
                                """))
                .andReturn();

        AuthResponse betaAuth = objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
        betaAccessToken = betaAuth.accessToken();
    }

    @Test
    void startOauth_shouldReturnAuthorizationUrl() throws Exception {
        mockMvc.perform(post("/api/v1/google-drive/oauth/start")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andExpect(jsonPath("$.redirectUri").value("http://localhost:8080/api/v1/google-drive/oauth/callback"))
                .andExpect(jsonPath("$.scopes[0]").value("https://www.googleapis.com/auth/drive"));
    }

    @Test
    void startOauth_shouldReturn403_whenUserDoesNotHaveBetaRole() throws Exception {
        mockMvc.perform(post("/api/v1/google-drive/oauth/start")
                        .header("Authorization", "Bearer " + nonBetaAccessToken))
                .andExpect(status().isForbidden());
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
                        .header("Authorization", "Bearer " + betaAccessToken))
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
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderIdOrUrl\":\"folder-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.rootFolderId").value("folder-123"))
                .andExpect(jsonPath("$.rootFolderName").value("Root Folder"));
    }

    @Test
    void disconnect_shouldRemoveConnectionAndReturnMessage() throws Exception {
        googleDriveConnectionRepository.save(buildConnection());
        assertThat(googleDriveConnectionRepository.findAll()).hasSize(1);

        mockMvc.perform(delete("/api/v1/google-drive/connection")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Google Drive connection removed"));

        assertThat(googleDriveConnectionRepository.findAll()).isEmpty();
    }

    @Test
    void disconnect_shouldSucceedEvenWithNoExistingConnection() throws Exception {
        mockMvc.perform(delete("/api/v1/google-drive/connection")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Google Drive connection removed"));
    }

    @Test
    void addBaseResume_shouldPersistResumeAndReturn201() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnection());
        googleDriveApiClient.fileMetadataById.put("doc-abc",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "doc-abc",
                        "My Resume",
                        GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE,
                        "https://docs.google.com/document/d/doc-abc/edit"
                ));

        mockMvc.perform(post("/api/v1/google-drive/base-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIdOrUrl\":\"doc-abc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.googleFileId").value("doc-abc"))
                .andExpect(jsonPath("$.documentName").value("My Resume"));

        assertThat(googleDriveBaseResumeRepository.findAllByConnectionIdOrderByCreatedAtAsc(connection.getId()))
                .hasSize(1);
    }

    @Test
    void addBaseResume_shouldRejectUnsupportedFileType() throws Exception {
        googleDriveConnectionRepository.save(buildConnection());
        googleDriveApiClient.fileMetadataById.put("docx-file",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "docx-file",
                        "Resume.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        null
                ));

        mockMvc.perform(post("/api/v1/google-drive/base-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIdOrUrl\":\"docx-file\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBaseResume_shouldRemoveResumeAndReturn200() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnection());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        googleDriveBaseResumeRepository.save(resume);

        mockMvc.perform(delete("/api/v1/google-drive/base-resumes/" + resume.getId())
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Base resume deleted successfully"));

        assertThat(googleDriveBaseResumeRepository.findAll()).isEmpty();
    }

    @Test
    void deleteBaseResume_shouldReturn404ForUnknownId() throws Exception {
        mockMvc.perform(delete("/api/v1/google-drive/base-resumes/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void copyResume_shouldCreateFolderAndCopyDocument() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnectionWithRootFolder());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        googleDriveBaseResumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setUser(userRepository.findByEmail("driveuser@example.com").orElseThrow());
        application.setVacancyName("Backend Engineer");
        application.setOrganization("Acme");
        application.setApplicationDate(LocalDate.now());
        application = applicationRepository.save(application);

        googleDriveApiClient.fileMetadataById.put("root-folder-id",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "root-folder-id",
                        "Job Tracker Root",
                        GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/root-folder-id"
                ));

        mockMvc.perform(post("/api/v1/google-drive/applications/" + application.getId() + "/resume-copies")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseResumeId\":\"" + resume.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(application.getId().toString()))
                .andExpect(jsonPath("$.baseResumeId").value(resume.getId().toString()))
                .andExpect(jsonPath("$.copiedFileId").value("copied-file"))
                .andExpect(jsonPath("$.vacancyFolderId").value("created-folder"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        JobApplication savedApplication = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(savedApplication.getDriveResumeFileId()).isEqualTo("copied-file");
        assertThat(savedApplication.getDriveResumeFileName()).contains("APP-" + application.getId());
        assertThat(savedApplication.getDriveResumeDocumentUrl()).contains("copied-file");
        assertThat(savedApplication.getDriveResumeGeneratedAt()).isNotNull();
    }

    @Test
    void copyResume_shouldReturn400WhenNoRootFolderConfigured() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnection());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        googleDriveBaseResumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setUser(userRepository.findByEmail("driveuser@example.com").orElseThrow());
        application.setVacancyName("SWE");
        application.setOrganization("Corp");
        application.setApplicationDate(LocalDate.now());
        application = applicationRepository.save(application);

        mockMvc.perform(post("/api/v1/google-drive/applications/" + application.getId() + "/resume-copies")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseResumeId\":\"" + resume.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detectResumePlaceholders_shouldReturnTemplatePlaceholders() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnection());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        googleDriveBaseResumeRepository.save(resume);

        mockMvc.perform(get("/api/v1/google-drive/resume-placeholders/" + resume.getId())
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseResumeId").value(resume.getId().toString()))
                .andExpect(jsonPath("$.applicationId").doesNotExist())
                .andExpect(jsonPath("$.placeholders[0]").value("SUMMARY"))
                .andExpect(jsonPath("$.placeholders[1]").value("SKILLS"));
    }

    @Test
    void generateResume_shouldReplaceTemplatePlaceholdersAndReturnGeneratedResume() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnectionWithRootFolder());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        googleDriveBaseResumeRepository.save(resume);

        JobApplication application = new JobApplication();
        application.setUser(userRepository.findByEmail("driveuser@example.com").orElseThrow());
        application.setVacancyName("Backend Engineer");
        application.setOrganization("Acme");
        application.setApplicationDate(LocalDate.now());
        application = applicationRepository.save(application);

        googleDriveApiClient.fileMetadataById.put("root-folder-id",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "root-folder-id",
                        "Job Tracker Root",
                        GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/root-folder-id"
                ));

        mockMvc.perform(post("/api/v1/google-drive/applications/" + application.getId() + "/generated-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseResumeId": "%s",
                                  "values": {
                                    "SUMMARY": "Senior Java Engineer",
                                    "SKILLS": "Spring Boot, PostgreSQL"
                                  }
                                }
                                """.formatted(resume.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(application.getId().toString()))
                .andExpect(jsonPath("$.baseResumeId").value(resume.getId().toString()))
                .andExpect(jsonPath("$.values.SUMMARY").value("Senior Java Engineer"))
                .andExpect(jsonPath("$.values.SKILLS").value("Spring Boot, PostgreSQL"))
                .andExpect(jsonPath("$.placeholders").isEmpty())
                .andExpect(jsonPath("$.copiedFileId").value("copied-file"))
                .andExpect(jsonPath("$.pdfFileId").value("pdf-file"))
                .andExpect(jsonPath("$.documentUrl").value("https://docs.google.com/document/d/copied-file/edit"))
                .andExpect(jsonPath("$.pdfUrl").value("https://docs.google.com/document/d/pdf-file/edit"))
                .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        JobApplication savedApplication = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(savedApplication.getDriveResumeFileId()).isEqualTo("copied-file");
        assertThat(savedApplication.getDriveResumeDocumentUrl()).contains("copied-file");
    }

    @Test
    void generateResume_shouldKeepUnresolvedPlaceholdersInResponse() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnectionWithRootFolder());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        googleDriveBaseResumeRepository.save(resume);
        googleDriveApiClient.setDocumentText("resume-file-id", "{{SUMMARY}}\n{{SKILLS}}\n{{UNKNOWN}}");

        JobApplication application = new JobApplication();
        application.setUser(userRepository.findByEmail("driveuser@example.com").orElseThrow());
        application.setVacancyName("Backend Engineer");
        application.setOrganization("Acme");
        application.setApplicationDate(LocalDate.now());
        application = applicationRepository.save(application);

        googleDriveApiClient.fileMetadataById.put("root-folder-id",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "root-folder-id",
                        "Job Tracker Root",
                        GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/root-folder-id"
                ));

        mockMvc.perform(post("/api/v1/google-drive/applications/" + application.getId() + "/generated-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "baseResumeId": "%s",
                                  "values": {
                                    "SUMMARY": "Senior Java Engineer",
                                    "SKILLS": "Spring Boot, PostgreSQL"
                                  }
                                }
                                """.formatted(resume.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.values.SUMMARY").value("Senior Java Engineer"))
                .andExpect(jsonPath("$.values.SKILLS").value("Spring Boot, PostgreSQL"))
                .andExpect(jsonPath("$.placeholders[0]").value("UNKNOWN"));

        assertThat(googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), "copied-file"))
                .contains("{{UNKNOWN}}");
    }

    @Test
    void listBaseResumes_shouldReturnEmptyListWhenNoResumes() throws Exception {
        googleDriveConnectionRepository.save(buildConnection());

        mockMvc.perform(get("/api/v1/google-drive/base-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listBaseResumes_shouldReturnRegisteredResumes() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnection());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        resume.setLanguage("EN");
        resume.setTemplate(true);
        googleDriveBaseResumeRepository.save(resume);

        mockMvc.perform(get("/api/v1/google-drive/base-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(resume.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Base Resume"))
                .andExpect(jsonPath("$[0].language").value("EN"))
                .andExpect(jsonPath("$[0].template").value(true))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void listBaseResumes_shouldReturn403_whenUserDoesNotHaveBetaRole() throws Exception {
        mockMvc.perform(get("/api/v1/google-drive/base-resumes")
                        .header("Authorization", "Bearer " + nonBetaAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getBaseResumeContent_shouldReturnDocumentText() throws Exception {
        GoogleDriveConnection connection = googleDriveConnectionRepository.save(buildConnection());
        GoogleDriveBaseResume resume = buildBaseResume(connection);
        resume.setLanguage("EN");
        resume.setTemplate(true);
        googleDriveBaseResumeRepository.save(resume);

        googleDriveApiClient.setDocumentText("resume-file-id", "{{SUMMARY}}\nSome resume content\n{{SKILLS}}");

        mockMvc.perform(get("/api/v1/google-drive/base-resumes/" + resume.getId() + "/content")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(resume.getId().toString()))
                .andExpect(jsonPath("$.name").value("Base Resume"))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.template").value(true))
                .andExpect(jsonPath("$.content").value("{{SUMMARY}}\nSome resume content\n{{SKILLS}}"));
    }

    @Test
    void getBaseResumeContent_shouldReturn404ForUnknownId() throws Exception {
        googleDriveConnectionRepository.save(buildConnection());

        mockMvc.perform(get("/api/v1/google-drive/base-resumes/" + UUID.randomUUID() + "/content")
                        .header("Authorization", "Bearer " + betaAccessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBaseResumeContent_shouldReturn403_whenUserDoesNotHaveBetaRole() throws Exception {
        mockMvc.perform(get("/api/v1/google-drive/base-resumes/" + UUID.randomUUID() + "/content")
                        .header("Authorization", "Bearer " + nonBetaAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void addBaseResume_shouldPersistLanguageAndTemplateFlag() throws Exception {
        googleDriveConnectionRepository.save(buildConnection());
        googleDriveApiClient.fileMetadataById.put("doc-en",
                new GoogleDriveApiClient.DriveFileMetadata(
                        "doc-en",
                        "BASE - CV - Vitor Hugo EN",
                        GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE,
                        "https://docs.google.com/document/d/doc-en/edit"
                ));

        mockMvc.perform(post("/api/v1/google-drive/base-resumes")
                        .header("Authorization", "Bearer " + betaAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentIdOrUrl\":\"doc-en\",\"language\":\"EN\",\"template\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentName").value("BASE - CV - Vitor Hugo EN"));

        List<GoogleDriveBaseResume> saved = googleDriveBaseResumeRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getLanguage()).isEqualTo("EN");
        assertThat(saved.getFirst().isTemplate()).isTrue();
    }

    private GoogleDriveConnection buildConnectionWithRootFolder() {
        GoogleDriveConnection connection = buildConnection();
        connection.setRootFolderId("root-folder-id");
        connection.setRootFolderName("Job Tracker Root");
        return connection;
    }

    private GoogleDriveBaseResume buildBaseResume(GoogleDriveConnection connection) {
        GoogleDriveBaseResume resume = new GoogleDriveBaseResume();
        resume.setConnection(connection);
        resume.setGoogleFileId("resume-file-id");
        resume.setDocumentName("Base Resume");
        resume.setWebViewLink("https://docs.google.com/document/d/resume-file-id/edit");
        return resume;
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
        private static final String DEFAULT_TEMPLATE_TEXT = "{{SUMMARY}}\n{{SKILLS}}";

        private OAuthTokens tokens = new OAuthTokens(
                "drive-access",
                "drive-refresh",
                LocalDateTime.now().plusHours(1),
                "https://www.googleapis.com/auth/drive"
        );
        private GoogleDriveAccountProfile accountProfile =
                new GoogleDriveAccountProfile("perm-123", "connected@example.com", "Drive User");
        private final Map<String, DriveFileMetadata> fileMetadataById = new HashMap<>();
        private final Map<String, String> documentTextById = new HashMap<>();

        void reset() {
            fileMetadataById.clear();
            documentTextById.clear();
            documentTextById.put("resume-file-id", DEFAULT_TEMPLATE_TEXT);
            documentTextById.put("copied-file", DEFAULT_TEMPLATE_TEXT);
        }

        void setDocumentText(String documentId, String text) {
            documentTextById.put(documentId, text);
        }

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
            String sourceText = documentTextById.getOrDefault(sourceFileId, DEFAULT_TEMPLATE_TEXT);
            documentTextById.put("copied-file", sourceText);
            return new DriveFileMetadata("copied-file", newName, GOOGLE_DOC_MIME_TYPE, null);
        }

        @Override
        public String readGoogleDocText(String accessToken, String documentId) {
            return documentTextById.getOrDefault(documentId, DEFAULT_TEMPLATE_TEXT);
        }

        @Override
        public void replaceGoogleDocPlaceholders(String accessToken, String documentId, Map<String, String> values) {
            if (values == null || values.isEmpty()) {
                return;
            }
            String updatedText = documentTextById.getOrDefault(documentId, DEFAULT_TEMPLATE_TEXT);
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String replacement = entry.getValue() == null ? "" : entry.getValue();
                String token = entry.getKey();
                if (token == null || token.isBlank()) {
                    continue;
                }
                String placeholderToken = "{{" + token.trim() + "}}";
                updatedText = updatedText.replace(placeholderToken, replacement);
            }
            documentTextById.put(documentId, updatedText);
        }

        @Override
        public DriveFileMetadata exportGoogleDocAsPdf(String accessToken, String documentId, String targetFolderId, String pdfName) {
            return new DriveFileMetadata("pdf-file", pdfName, "application/pdf", null);
        }

        @Override
        public byte[] downloadFileBytes(String accessToken, String fileId) {
            return new byte[0];
        }
    }
}
