package com.jobtracker.e2e;

import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.PasswordResetTokenRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class AuthE2ETest extends AbstractE2ETest {

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

    /**
     * Complete auth flow: register -> validate token -> login -> refresh -> logout
     * Verifies that refresh tokens are stored in HttpOnly cookies and rotated on refresh
     */
    @Test
    void fullAuthFlow_register_login_refresh_logout() {
        // 1. Register
        Response registerResponse = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "E2E User",
                          "email": "e2e@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234"
                        }
                        """)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", nullValue()) // No refresh token in JSON body
                .body("user.email", equalTo("e2e@example.com"))
                .extract().response();

        String accessToken = registerResponse.jsonPath().getString("accessToken");
        assertThat(accessToken).isNotBlank();

        // Verify refresh token is in Set-Cookie header
        String refreshTokenFromCookie = extractRefreshTokenFromCookies(registerResponse);
        assertThat(refreshTokenFromCookie).isNotNull();

        // 2. Get current user with access token
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("e2e@example.com"))
                .body("name", equalTo("E2E User"));

        // 3. Login
        Response loginResponse = given()
                .contentType("application/json")
                .body("""
                        {
                          "email": "e2e@example.com",
                          "password": "pass1234"
                        }
                        """)
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", nullValue()) // No refresh token in JSON body
                .extract().response();

        String loginAccessToken = loginResponse.jsonPath().getString("accessToken");
        String loginRefreshToken = extractRefreshTokenFromCookies(loginResponse);
        assertThat(loginRefreshToken).isNotNull();

        // 4. Refresh token using the cookie
        Response refreshResponse = given()
                .header("Cookie", "refreshToken=" + loginRefreshToken)
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/v1/auth/refresh")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", nullValue()) // No refresh token in JSON body
                .extract().response();

        String newAccessToken = refreshResponse.jsonPath().getString("accessToken");
        String newRefreshToken = extractRefreshTokenFromCookies(refreshResponse);
        assertThat(newAccessToken).isNotBlank();
        assertThat(newRefreshToken).isNotNull();
        // Refresh tokens should be rotated (different tokens)
        assertThat(newRefreshToken).isNotEqualTo(loginRefreshToken);

        // 5. Old refresh token should be invalidated (revoked)
        given()
                .header("Cookie", "refreshToken=" + loginRefreshToken)
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/v1/auth/refresh")
                .then()
                .statusCode(401);

        // 6. Logout using the new refresh token
        given()
                .header("Cookie", "refreshToken=" + newRefreshToken)
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/v1/auth/logout")
                .then()
                .statusCode(200)
                .body("message", containsString("Logged out"));

        // 7. Logout-ed token should no longer work
        given()
                .header("Cookie", "refreshToken=" + newRefreshToken)
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/v1/auth/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    void register_shouldSetHttpOnlySecureSameSiteCookie() {
        Response response = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Cookie User",
                          "email": "cookies@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234"
                        }
                        """)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .extract().response();

        // Verify Set-Cookie header has security attributes
        String setCookieHeader = response.getHeader("Set-Cookie");
        assertThat(setCookieHeader).contains("Path=/api/v1/auth/refresh");
        assertThat(setCookieHeader).contains("HttpOnly");
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
    }

    @Test
    void register_shouldReturn409_whenEmailDuplicated() {
        String body = """
                {
                  "name": "Dup User",
                  "email": "dup@example.com",
                  "password": "pass1234",
                  "confirmPassword": "pass1234"
                }
                """;

        given().contentType("application/json").body(body).post("/api/v1/auth/register")
                .then().statusCode(201);

        given().contentType("application/json").body(body).post("/api/v1/auth/register")
                .then().statusCode(409);
    }

    @Test
    void register_shouldReturn400_whenPasswordsMismatch() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Mismatch User",
                          "email": "mismatch@example.com",
                          "password": "pass1234",
                          "confirmPassword": "different"
                        }
                        """)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    void login_shouldReturn401_whenWrongPassword() {
        // Register first
        given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Wrong Pass",
                          "email": "wrongpass@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234"
                        }
                        """)
                .post("/api/v1/auth/register")
                .then().statusCode(201);

        // Login with wrong password
        given()
                .contentType("application/json")
                .body("{\"email\": \"wrongpass@example.com\", \"password\": \"badpass\"}")
                .post("/api/v1/auth/login")
                .then().statusCode(401);
    }

    @Test
    void refresh_shouldReturn401_whenTokenMissing() {
        given()
                .contentType("application/json")
                .body("{}")
                .post("/api/v1/auth/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    void logout_shouldReturnSuccess_andClearCookie() {
        // Register and get refresh token
        Response regResponse = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "Logout User",
                          "email": "logout@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234"
                        }
                        """)
                .when()
                .post("/api/v1/auth/register")
                .then()
                .statusCode(201)
                .extract().response();

        String refreshToken = extractRefreshTokenFromCookies(regResponse);
        assertThat(refreshToken).isNotNull();

        // Logout
        Response logoutResponse = given()
                .header("Cookie", "refreshToken=" + refreshToken)
                .contentType("application/json")
                .body("{}")
                .when()
                .post("/api/v1/auth/logout")
                .then()
                .statusCode(200)
                .body("message", containsString("Logged out"))
                .extract().response();

        // Verify Set-Cookie clears the cookie (Max-Age=0)
        String setCookieHeader = logoutResponse.getHeader("Set-Cookie");
        assertThat(setCookieHeader).contains("Max-Age=0");
    }

    @Test
    void forgotPassword_shouldReturn200_regardlessOfEmailExistence() {
        given()
                .contentType("application/json")
                .body("{\"email\": \"doesnotexist@example.com\"}")
                .post("/api/v1/auth/forgot-password")
                .then().statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenNoToken() {
        given()
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void me_shouldReturn401_whenAccessTokenInvalidOrExpired() {
        given()
                .header("Authorization", "Bearer invalid.token.here")
                .when()
                .get("/api/v1/auth/me")
                .then()
                .statusCode(401);
    }

    /**
     * Helper method to extract the refresh token value from Set-Cookie headers
     */
    private String extractRefreshTokenFromCookies(Response response) {
        String setCookieHeader = response.getHeader("Set-Cookie");
        if (setCookieHeader == null || !setCookieHeader.contains("refreshToken=")) {
            return null;
        }
        // Parse: refreshToken=<token>; Path=/auth/refresh...
        String[] parts = setCookieHeader.split(";")[0].split("=", 2);
        if (parts.length == 2) {
            return parts[1];
        }
        return null;
    }
}
