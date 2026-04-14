package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.LoginRequest;
import com.jobtracker.dto.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "resilience4j.ratelimiter.instances.authLogin.limit-for-period=1",
        "resilience4j.ratelimiter.instances.authLogin.limit-refresh-period=10m",
        "resilience4j.ratelimiter.instances.authLogin.timeout-duration=0"
})
class AuthRateLimiterIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void login_shouldReturn429_whenRateLimitIsExceeded() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest("Rate Limit User", "ratelimit@example.com", "pass1234", "pass1234");
        LoginRequest loginRequest = new LoginRequest("ratelimit@example.com", "pass1234");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }
}
