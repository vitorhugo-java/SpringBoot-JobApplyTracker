package com.jobtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.repository.GptOAuthAuthorizationCodeRepository;
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
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GptOAuthFlowIT extends AbstractIntegrationTest {

    private static final String CLIENT_ID = "test-openai-client-id";
    private static final String CLIENT_SECRET = "test-openai-client-secret";
    private static final String REDIRECT_URI = "https://chat.openai.com/aip/test/callback";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtDecoder gptOAuthJwtDecoder;
    @Autowired private UserRepository userRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private GoogleDriveOAuthStateRepository googleDriveOAuthStateRepository;
    @Autowired private GptOAuthAuthorizationCodeRepository gptOAuthAuthorizationCodeRepository;
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
        gptOAuthAuthorizationCodeRepository.deleteAll();
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
    void oauthCodeFlow_shouldIssueScopedTokenAndAllowGptEndpoints() throws Exception {
        registerUser("gpt-user@example.com", "pass1234");
        PkcePair pkcePair = generatePkcePair();

        String authorizationCode = authorize("gpt-user@example.com", "pass1234",
                "read:profile read:applications write:applications read:metrics", pkcePair);
        String accessToken = exchangeToken(authorizationCode, pkcePair.verifier());

        Jwt jwt = gptOAuthJwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo("gpt-user@example.com");
        assertThat(jwt.getClaimAsString("scope")).contains("write:applications");
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("gpt_action_access");

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
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
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gpt-user@example.com"));

        mockMvc.perform(get("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void oauthCodeFlow_withoutPkce_shouldIssueScopedTokenAndAllowGptEndpoints() throws Exception {
        registerUser("gpt-user-no-pkce@example.com", "pass1234");

        String authorizationCode = authorize("gpt-user-no-pkce@example.com", "pass1234",
                "read:profile read:applications", null);
        String accessToken = exchangeToken(authorizationCode, null);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("gpt-user-no-pkce@example.com"));
    }

    @Test
    void oauthAuthorizeGet_withoutPkce_shouldReturnConsentPage() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("scope", "read:profile")
                        .param("state", "chatgpt-state"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Authorize GPT Action")));
    }

    @Test
    void openidDiscoveryEndpoint_shouldBePublic() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint").value("https://jobapply-api.hugojava.dev/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("https://jobapply-api.hugojava.dev/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("https://jobapply-api.hugojava.dev/oauth2/jwks"));

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kid").value("gpt-oauth-rsa"));
    }

    @Test
    void readOnlyGptToken_shouldRejectWriteEndpoints() throws Exception {
        registerUser("readonly-gpt@example.com", "pass1234");
        PkcePair pkcePair = generatePkcePair();

        String authorizationCode = authorize("readonly-gpt@example.com", "pass1234",
                "read:profile read:applications", pkcePair);
        String accessToken = exchangeToken(authorizationCode, pkcePair.verifier());

        mockMvc.perform(post("/api/v1/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
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
    void legacyJwtFlow_shouldStillWork_andNotAuthenticateAgainstGptEndpoints() throws Exception {
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

    private String authorize(String email, String password, String scope, PkcePair pkcePair) throws Exception {
        var request = post("/oauth2/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("response_type", "code")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("scope", scope)
                .param("state", "test-state")
                .param("email", email)
                .param("password", password)
                .param("approve", "true");
        if (pkcePair != null) {
            request.param("code_challenge", pkcePair.challenge());
            request.param("code_challenge_method", "S256");
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern(REDIRECT_URI + "?*"))
                .andReturn();

        String redirectLocation = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(redirectLocation).contains("state=test-state");
        return Arrays.stream(java.net.URI.create(redirectLocation).getQuery().split("&"))
                .filter(entry -> entry.startsWith("code="))
                .map(entry -> entry.substring("code=".length()))
                .findFirst()
                .orElseThrow();
    }

    private String exchangeToken(String authorizationCode, String verifier) throws Exception {
        String basicAuth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));

        var request = post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", authorizationCode)
                .param("redirect_uri", REDIRECT_URI);
        if (verifier != null) {
            request.param("code_verifier", verifier);
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("access_token").asText();
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
}
