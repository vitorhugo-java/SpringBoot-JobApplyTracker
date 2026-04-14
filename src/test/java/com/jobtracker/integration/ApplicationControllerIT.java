package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        applicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest reg = new RegisterRequest("App User", "appuser@example.com", "pass1234", "pass1234");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
    }

    @Test
    void createApplication_shouldReturn201() throws Exception {
        ApplicationRequest request = buildRequest("Software Engineer");

        mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.vacancyName").value("Software Engineer"))
                .andExpect(jsonPath("$.status").value("RH"));
    }

        @Test
        void createApplication_shouldAllowBlankVacancyName() throws Exception {
                ApplicationRequest request = buildRequest("   ");

                mockMvc.perform(post("/api/applications")
                                                .header("Authorization", "Bearer " + accessToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.vacancyName").value(nullValue()));
        }

    @Test
    void createApplication_shouldReturn403_whenNotAuthenticated() throws Exception {
        ApplicationRequest request = buildRequest("Backend Dev");

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_shouldReturn200_whenFound() throws Exception {
        ApplicationRequest request = buildRequest("Senior Dev");
        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/applications/{id}", id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/applications/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateApplication_shouldReturn200() throws Exception {
        ApplicationRequest create = buildRequest("Junior Dev");
        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        ApplicationRequest update = buildRequest("Senior Dev Updated");
        mockMvc.perform(put("/api/applications/{id}", id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vacancyName").value("Senior Dev Updated"));
    }

    @Test
    void updateStatus_shouldReturn200() throws Exception {
        ApplicationRequest create = buildRequest("Status Test");
        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/applications/{id}/status", id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"Teste Técnico\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Teste Técnico"));
    }

    @Test
    void deleteApplication_shouldReturn200() throws Exception {
        ApplicationRequest create = buildRequest("Delete Me");
        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/applications/{id}", id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Application deleted successfully"));

        mockMvc.perform(get("/api/applications/{id}", id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_shouldReturnPagedResponse() throws Exception {
        mockMvc.perform(post("/api/applications")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest("App 1"))));
        mockMvc.perform(post("/api/applications")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest("App 2"))));

        mockMvc.perform(get("/api/applications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void updateReminder_shouldReturn200() throws Exception {
        ApplicationRequest create = buildRequest("Reminder Test");
        MvcResult createResult = mockMvc.perform(post("/api/applications")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/api/applications/{id}/reminder", id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recruiterDmReminderEnabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recruiterDmReminderEnabled").value(true));
    }

    private ApplicationRequest buildRequest(String vacancyName) {
        return new ApplicationRequest(
                vacancyName, "Some Recruiter", "HR Department",
                "https://example.com/job", LocalDate.now().minusDays(1),
                false, false, null, "RH", false
        );
    }
}
