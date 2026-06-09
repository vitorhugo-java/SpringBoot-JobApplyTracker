package com.jobtracker.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies CIMD (Client ID Metadata Document) support in the authorization server.
 *
 * When ChatGPT presents the URL of its metadata document as the {@code client_id}, the
 * {@link com.jobtracker.config.CimdRegisteredClientRepository} fetches that document, builds an
 * ephemeral {@link RegisteredClient}, and the authorization-code flow proceeds as usual.
 *
 * <p>The CIMD fetch {@code RestClient} is replaced with one backed by {@link MockRestServiceServer}
 * so no real network call is made. A raw public-IP host is used as the client_id so the SSRF guard
 * passes without a DNS lookup.
 */
@Import(CimdRegisteredClientIT.CimdTestConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class CimdRegisteredClientIT extends AbstractIntegrationTest {

    private static final String CLIENT_ID_URL = "https://93.184.216.34/oauth/test/client.json";
    private static final String REDIRECT_URI = "https://chat.openai.com/aip/test/callback";

    @Autowired private MockMvc mockMvc;
    @Autowired private RegisteredClientRepository registeredClientRepository;

    @BeforeEach
    void resetMockServer() {
        CimdTestConfig.SERVER.get().reset();
    }

    @Test
    void authorizationRequest_withCimdUrlAsClientId_shouldFetchDocumentAndRedirectWithCode() throws Exception {
        CimdTestConfig.SERVER.get()
                .expect(ExpectedCount.manyTimes(), requestTo(CLIENT_ID_URL))
                .andRespond(withSuccess("""
                        {
                          "client_id": "%s",
                          "client_name": "ChatGPT CIMD Test",
                          "redirect_uris": ["%s"],
                          "scope": "openid read:profile read:applications",
                          "grant_types": ["authorization_code"],
                          "token_endpoint_auth_method": "none"
                        }
                        """.formatted(CLIENT_ID_URL, REDIRECT_URI), MediaType.APPLICATION_JSON));

        PkcePair pkcePair = generatePkcePair();

        MvcResult result = mockMvc.perform(get("/oauth2/authorize")
                        .with(user("cimd-user@example.com").roles("USER"))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID_URL)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid read:profile")
                        .queryParam("state", "cimd-state")
                        .queryParam("code_challenge", pkcePair.challenge())
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(REDIRECT_URI + "?*"))
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).contains("code=");
        assertThat(location).contains("state=cimd-state");
    }

    @Test
    void authorizationRequest_withCimdUrlNotListingRequestedRedirect_shouldBeRejected() throws Exception {
        CimdTestConfig.SERVER.get()
                .expect(ExpectedCount.manyTimes(), requestTo(CLIENT_ID_URL))
                .andRespond(withSuccess("""
                        {
                          "client_id": "%s",
                          "redirect_uris": ["https://other.example.com/callback"],
                          "scope": "openid read:profile"
                        }
                        """.formatted(CLIENT_ID_URL), MediaType.APPLICATION_JSON));

        PkcePair pkcePair = generatePkcePair();

        // The requested redirect_uri is not in the CIMD document, so the AS must not redirect to it.
        mockMvc.perform(get("/oauth2/authorize")
                        .with(user("cimd-user@example.com").roles("USER"))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID_URL)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid read:profile")
                        .queryParam("state", "cimd-state")
                        .queryParam("code_challenge", pkcePair.challenge())
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonUrlClientId_shouldStillResolveViaJdbcRepository() {
        // The bootstrapped GPT Actions client (configured in application-test.yml) must keep working
        // through the JDBC delegate even though the primary repository is the CIMD wrapper.
        RegisteredClient client = registeredClientRepository.findByClientId("test-openai-client-id");
        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo("test-openai-client-id");
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

    @TestConfiguration
    static class CimdTestConfig {

        static final AtomicReference<MockRestServiceServer> SERVER = new AtomicReference<>();

        // Overrides the production cimdRestClient bean with one bound to MockRestServiceServer so
        // CIMD document fetches are intercepted instead of hitting the network.
        @Bean(name = "cimdRestClient")
        RestClient cimdRestClient() {
            RestClient.Builder builder = RestClient.builder();
            SERVER.set(MockRestServiceServer.bindTo(builder).build());
            return builder.build();
        }
    }
}
