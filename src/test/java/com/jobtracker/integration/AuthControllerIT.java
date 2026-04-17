package com.jobtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.AuthResponse;
import com.jobtracker.dto.auth.LoginRequest;
import com.jobtracker.dto.auth.RegisterRequest;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
        @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
        @Autowired private ApplicationRepository applicationRepository;

    @BeforeEach
    void cleanDb() {
                applicationRepository.deleteAll();
                passwordResetTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void register_shouldReturn201_setRefreshTokenCookie_andReturnAccessToken() throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", "register@example.com", "pass1234", "pass1234");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("register@example.com"))
                // Refresh token should NOT be in JSON body (now in HttpOnly cookie)
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // Verify Set-Cookie header with HttpOnly, Secure, SameSite
        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).isNotEmpty();
        String refreshCookie = cookies.stream()
                .filter(c -> c.contains("Path=/api/v1/auth/refresh"))
                .findFirst()
                .orElseThrow();
        assertThat(refreshCookie).contains("HttpOnly");
        assertThat(refreshCookie).contains("Secure");
        assertThat(refreshCookie).contains("SameSite=Lax");

        AuthResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(response.accessToken()).isNotBlank();
    }

    @Test
    void register_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", "duplicate@example.com", "pass1234", "pass1234");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_shouldReturn400_whenPasswordsDoNotMatch() throws Exception {
        RegisterRequest request = new RegisterRequest("Test User", "mismatch@example.com", "pass1234", "different");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_shouldReturn200_setRefreshTokenCookie_andReturnAccessToken() throws Exception {
        // First register
        RegisterRequest reg = new RegisterRequest("Login User", "login@example.com", "pass1234", "pass1234");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Then login
        LoginRequest login = new LoginRequest("login@example.com", "pass1234");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // Verify refresh token is set as HttpOnly cookie
        List<String> cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).isNotEmpty();
        String refreshCookie = cookies.stream()
                .filter(c -> c.contains("Path=/api/v1/auth/refresh"))
                .findFirst()
                .orElseThrow();
        assertThat(refreshCookie).contains("HttpOnly");
    }

    @Test
    void login_shouldReturn401_whenBadCredentials() throws Exception {
        LoginRequest login = new LoginRequest("nobody@example.com", "wrongpass");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_shouldReadFromCookie_returnNewAccessToken_andRotateRefreshTokenCookie() throws Exception {
        // Register to get initial tokens
        RegisterRequest reg = new RegisterRequest("Refresh User", "refresh@example.com", "pass1234", "pass1234");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        // Extract the refresh token from Set-Cookie header
        List<String> cookies = regResult.getResponse().getHeaders("Set-Cookie");
        String refreshCookie = cookies.stream()
                .filter(c -> c.contains("Path=/api/v1/auth/refresh"))
                .findFirst()
                .orElseThrow();
        
        // Extract token value from cookie
        String refreshTokenValue = refreshCookie.split(";")[0].split("=", 2)[1];

        // Call refresh endpoint with empty body and refresh token in cookie
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("refreshToken", refreshTokenValue))
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        // Verify new refresh token is set in Set-Cookie header (rotation)
        List<String> newCookies = refreshResult.getResponse().getHeaders("Set-Cookie");
        assertThat(newCookies).isNotEmpty();
        String newRefreshCookie = newCookies.stream()
                .filter(c -> c.contains("Path=/api/v1/auth/refresh"))
                .findFirst()
                .orElseThrow();
        assertThat(newRefreshCookie).contains("HttpOnly");
        assertThat(newRefreshCookie).contains("Secure");
    }

    @Test
    void refresh_shouldReturn401_whenRefreshTokenIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refreshToken", "invalid-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_shouldClearRefreshTokenCookie() throws Exception {
        // Register
        RegisterRequest reg = new RegisterRequest("Logout User", "logout@example.com", "pass1234", "pass1234");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        // Extract refresh token from cookie
        List<String> cookies = regResult.getResponse().getHeaders("Set-Cookie");
        String refreshCookie = cookies.stream()
                .filter(c -> c.contains("Path=/api/v1/auth/refresh"))
                .findFirst()
                .orElseThrow();
        String refreshTokenValue = refreshCookie.split(";")[0].split("=", 2)[1];

        // Logout
        MvcResult logoutResult = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refreshToken", refreshTokenValue))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn();

        // Verify Set-Cookie header clears the refresh token (Max-Age=0)
        List<String> logoutCookies = logoutResult.getResponse().getHeaders("Set-Cookie");
        String clearedCookie = logoutCookies.stream()
                .filter(c -> c.contains("Path=/api/v1/auth/refresh"))
                .findFirst()
                .orElseThrow();
        assertThat(clearedCookie).contains("Max-Age=0");
    }

    @Test
    void me_shouldReturn200_whenAuthenticated() throws Exception {
        RegisterRequest reg = new RegisterRequest("Me User", "me@example.com", "pass1234", "pass1234");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }

    @Test
    void me_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPassword_shouldReturn200_regardlessOfEmailExistence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateProfile_shouldReturn200_whenAuthenticated() throws Exception {
        RegisterRequest reg = new RegisterRequest("Profile User", "profile@example.com", "pass1234", "pass1234");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(put("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Updated Profile User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Profile User"))
                .andExpect(jsonPath("$.email").value("profile@example.com"));
    }

    @Test
    void changePassword_shouldReturn200_andAllowLoginWithNewPassword() throws Exception {
        RegisterRequest reg = new RegisterRequest("Password User", "password-change@example.com", "pass1234", "pass1234");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"pass1234\",\"newPassword\":\"newpass1234\",\"confirmPassword\":\"newpass1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));

        LoginRequest login = new LoginRequest("password-change@example.com", "newpass1234");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void changePassword_shouldReturn400_whenCurrentPasswordIsInvalid() throws Exception {
        RegisterRequest reg = new RegisterRequest("Password User", "password-invalid@example.com", "pass1234", "pass1234");
        MvcResult regResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(regResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(put("/api/v1/auth/me/password")
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong1234\",\"newPassword\":\"newpass1234\",\"confirmPassword\":\"newpass1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }
}
