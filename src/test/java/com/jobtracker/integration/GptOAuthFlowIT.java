package com.jobtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.repository.WebAuthnChallengeRepository;
import com.jobtracker.repository.WebAuthnCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GptOAuthFlowIT extends AbstractIntegrationTest {

    private static final String CLIENT_ID = "test-openai-client-id";
    private static final String CLIENT_SECRET = "test-openai-client-secret";
    private static final String REDIRECT_URI = "https://chat.openai.com/aip/test/callback";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder authorizationServerJwtDecoder;
    @Autowired private UserRepository userRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private GoogleDriveOAuthStateRepository googleDriveOAuthStateRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private InterviewEventRepository interviewEventRepository;
    @Autowired private UserGamificationRepository userGamificationRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private UserInterviewMetricsRepository userInterviewMetricsRepository;
    @Autowired private WebAuthnChallengeRepository webAuthnChallengeRepository;
    @Autowired private WebAuthnCredentialRepository webAuthnCredentialRepository;

    @BeforeEach
    void cleanDb() {
        googleDriveOAuthStateRepository.deleteAll();
        googleDriveConnectionRepository.deleteAll();
        userAchievementRepository.deleteAll();
        userGamificationRepository.deleteAll();
        interviewEventRepository.deleteAll();
        applicationRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        webAuthnChallengeRepository.deleteAll();
        webAuthnCredentialRepository.deleteAll();
        userInterviewMetricsRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void openidDiscoveryAndJwksEndpoints_shouldBePublic() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value("https://jobapply-api.hugojava.dev/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("https://jobapply-api.hugojava.dev/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("https://jobapply-api.hugojava.dev/oauth2/jwks"))
                .andExpect(jsonPath("$.scopes_supported").isArray());

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void oauthCodeFlow_shouldIssueScopedTokenAndAllowGptEndpoints() throws Exception {
        registerUser("gpt-user@example.com", "pass1234");
        PkcePair pkcePair = generatePkcePair();

        String authorizationCode = authorizeWithPkce("gpt-user@example.com", "pass1234",
                "openid read:profile read:applications write:applications read:metrics", pkcePair);
        TokenResponse tokenResponse = exchangeTokenWithBasic(authorizationCode, pkcePair.verifier());

        Jwt jwt = authorizationServerJwtDecoder.decode(tokenResponse.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("gpt-user@example.com");
        assertThat(jwt.getClaimAsStringList("scope"))
                .contains("write:applications", "read:profile");
        assertThat(jwt.getClaimAsStringList("roles")).contains("ROLE_GPT_CLIENT");
        assertThat(jwt.getClaimAsString("user_id")).isNotBlank();
        assertThat(tokenResponse.idToken()).isNotBlank();
        assertThat(tokenResponse.refreshToken()).isNotBlank();

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vacancyName": "GPT Backend Engineer",
                                  "organization": "OpenAI",
                                  "vacancyLink": "https://example.com/jobs/gpt-backend",
                                  "applicationDate": "2026-05-01",
                                  "rhAcceptedConnection": false,
                                  "interviewScheduled": false,
                                  "status": "RH",
                                  "recruiterDmReminderEnabled": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vacancyName").value("GPT Backend Engineer"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gpt-user@example.com"));

        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pkceProtectedCodeFlow_shouldRejectInvalidVerifier() throws Exception {
        registerUser("pkce-user@example.com", "pass1234");
        PkcePair pkcePair = generatePkcePair();
        String authorizationCode = authorizeWithPkce("pkce-user@example.com", "pass1234",
                "openid read:profile read:applications", pkcePair);

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", "wrong-verifier"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenExchange_shouldSupportClientSecretPost() throws Exception {
        registerUser("post-client@example.com", "pass1234");
        PkcePair pkcePair = generatePkcePair();
        String authorizationCode = authorizeWithPkce("post-client@example.com", "pass1234",
                "openid read:profile read:applications", pkcePair);

        TokenResponse tokenResponse = exchangeTokenWithPost(authorizationCode, pkcePair.verifier());

        assertThat(tokenResponse.accessToken()).isNotBlank();
        assertThat(tokenResponse.refreshToken()).isNotBlank();
    }

    @Test
    void readOnlyGptToken_shouldRejectWriteEndpoints() throws Exception {
        registerUser("readonly-gpt@example.com", "pass1234");
        PkcePair pkcePair = generatePkcePair();

        String authorizationCode = authorizeWithPkce("readonly-gpt@example.com", "pass1234",
                "openid read:profile read:applications", pkcePair);
        TokenResponse tokenResponse = exchangeTokenWithBasic(authorizationCode, pkcePair.verifier());

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vacancyName": "Denied Write",
                                  "organization": "OpenAI",
                                  "vacancyLink": "https://example.com/jobs/readonly",
                                  "applicationDate": "2026-05-01",
                                  "rhAcceptedConnection": false,
                                  "interviewScheduled": false,
                                  "status": "RH",
                                  "recruiterDmReminderEnabled": false
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyJwtFlow_shouldStillWork() throws Exception {
        AuthResponse authResponse = registerUser("legacy-user@example.com", "pass1234");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("legacy-user@example.com"));

        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void googleDriveCallback_shouldRemainPublic() throws Exception {
        mockMvc.perform(get("/api/v1/google-drive/oauth/callback")
                        .param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("status=error")));
    }

    private AuthResponse registerUser(String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest("GPT User", email, password, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String authorizeWithPkce(String email, String password, String scope, PkcePair pkcePair) throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorize")
                        .with(user(email).roles("USER"))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", scope)
                        .queryParam("state", "test-state")
                        .queryParam("code_challenge", pkcePair.challenge())
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(REDIRECT_URI + "?*"))
                .andReturn();

        String redirectLocation = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(redirectLocation).contains("state=test-state");
        return extractQueryParam(redirectLocation, "code");
    }

    private TokenResponse exchangeTokenWithBasic(String authorizationCode, String verifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        return toTokenResponse(result);
    }

    private TokenResponse exchangeTokenWithPost(String authorizationCode, String verifier) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", CLIENT_SECRET)
                        .param("code", authorizationCode)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andReturn();
        return toTokenResponse(result);
    }

    private TokenResponse toTokenResponse(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenResponse(
                json.get("access_token").asText(),
                json.path("refresh_token").asText(null),
                json.path("id_token").asText(null));
    }

    private String basicAuthHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    }

    private String extractQueryParam(String redirectLocation, String parameterName) {
        String prefix = parameterName + "=";
        for (String entry : java.net.URI.create(redirectLocation).getQuery().split("&")) {
            if (entry.startsWith(prefix)) {
                return entry.substring(prefix.length());
            }
        }
        throw new IllegalStateException("Missing query parameter: " + parameterName);
    }

    private PkcePair generatePkcePair() throws Exception {
        String verifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("test-code-verifier-1234567890".getBytes(StandardCharsets.US_ASCII));
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return new PkcePair(verifier, challenge);
    }

    private record PkcePair(String verifier, String challenge) {
    }

    private record TokenResponse(String accessToken, String refreshToken, String idToken) {
    }
}
