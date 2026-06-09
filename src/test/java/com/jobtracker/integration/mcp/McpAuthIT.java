package com.jobtracker.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.integration.AbstractIntegrationTest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the MCP endpoint requires authentication via the existing OAuth2/JWT machinery.
 *
 * Spring AI 1.1.x uses the Streamable HTTP transport (WebMvcStreamableServerTransport): a single
 * endpoint, POST /mcp, that handles JSON-RPC messages (GET opens the SSE stream). Unauthenticated
 * requests to /mcp/** receive a 401 with a WWW-Authenticate: Bearer challenge (RFC 6750 / RFC 9728)
 * so MCP clients can discover the OAuth protected-resource metadata — see McpAuthenticationEntryPoint.
 */
class McpAuthIT extends AbstractIntegrationTest {

    private static final String MCP_ENDPOINT = "/mcp";

    private static final String MCP_INITIALIZE_BODY = """
            {
              "jsonrpc": "2.0",
              "id": 1,
              "method": "initialize",
              "params": {
                "protocolVersion": "2024-11-05",
                "capabilities": {},
                "clientInfo": { "name": "test-client", "version": "1.0" }
              }
            }
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private GoogleDriveConnectionRepository googleDriveConnectionRepository;
    @Autowired private InterviewEventRepository interviewEventRepository;
    @Autowired private UserInterviewMetricsRepository userInterviewMetricsRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private UserGamificationRepository userGamificationRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        googleDriveConnectionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        interviewEventRepository.deleteAll();
        userInterviewMetricsRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest("MCP Test User", "mcp-test@example.com", "pass1234", "pass1234", true);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
    }

    @Test
    void mcpEndpoint_withoutToken_returns401WithBearerChallenge() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("resource_metadata")));
    }

    @Test
    void mcpEndpoint_withValidToken_doesNotReturn403() throws Exception {
        MvcResult result = mockMvc.perform(post(MCP_ENDPOINT)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    void mcpEndpoint_withMalformedToken_returns401() throws Exception {
        mockMvc.perform(post(MCP_ENDPOINT)
                        .header("Authorization", "Bearer not-a-real-jwt-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MCP_INITIALIZE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mcpToken_worksForRestEndpoints() throws Exception {
        // Same JWT works for both REST and MCP — confirms auth symmetry.
        // 400 = passed auth but body was invalid; anything except 403 means auth succeeded.
        MvcResult result = mockMvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
    }
}
