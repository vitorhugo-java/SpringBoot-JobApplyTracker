package com.jobtracker.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GptFallbackAuthDisabledIT extends AbstractIntegrationTest {

    private static final String FALLBACK_TOKEN = "test-gpt-fallback-token";

    @Autowired private MockMvc mockMvc;

    @Test
    void fallbackBearerTokenShouldBeIgnoredWhenFeatureIsDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + FALLBACK_TOKEN))
                .andExpect(status().isForbidden());
    }
}
