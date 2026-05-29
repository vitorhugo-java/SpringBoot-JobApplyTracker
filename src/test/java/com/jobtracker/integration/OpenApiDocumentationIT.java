package com.jobtracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiDocumentationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void googleDriveGroup_shouldContainGoogleDrivePathsAndServer() throws Exception {
        JsonNode openApi = fetchOpenApiGroup("google-drive");

        assertThat(openApi.at("/info/title").asText()).isEqualTo("JobApply API");
        assertThat(openApi.at("/servers/0/url").asText()).isEqualTo("https://jobapply-api.hugojava.dev");
        assertThat(openApi.path("paths").has("/api/v1/google-drive/status")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/google-drive/oauth/start")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/applications")).isFalse();
    }

    @Test
    void applicationsGroup_shouldContainApplicationPathsAndServer() throws Exception {
        JsonNode openApi = fetchOpenApiGroup("applications");

        assertThat(openApi.at("/info/title").asText()).isEqualTo("JobApply API");
        assertThat(openApi.at("/servers/0/url").asText()).isEqualTo("https://jobapply-api.hugojava.dev");
        assertThat(openApi.path("paths").has("/api/v1/applications")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/applications/link-metadata")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/google-drive/status")).isFalse();
    }

    @Test
    void gptActionsGroup_shouldContainOauthSchemeAndExistingApiPaths() throws Exception {
        JsonNode openApi = fetchOpenApiGroup("gpt-actions");

        assertThat(openApi.path("paths").has("/api/v1/auth/me")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/applications")).isTrue();
        assertThat(openApi.path("paths").has("/oauth2/token")).isTrue();
        assertThat(openApi.at("/components/securitySchemes/gptOAuth/type").asText()).isEqualTo("oauth2");
        assertThat(openApi.at("/components/securitySchemes/gptOAuth/flows/authorizationCode/authorizationUrl").asText())
                .isEqualTo("https://jobapply-api.hugojava.dev/oauth2/authorize");
    }

    private JsonNode fetchOpenApiGroup(String group) throws Exception {
        String response = mockMvc.perform(get("/v3/api-docs/{group}", group))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }
}
