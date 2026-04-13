package com.jobtracker.e2e;

import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class AuthE2ETest extends AbstractE2ETest {

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDb() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

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
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("user.email", equalTo("e2e@example.com"))
                .extract().response();

        String accessToken = registerResponse.jsonPath().getString("accessToken");
        String refreshToken = registerResponse.jsonPath().getString("refreshToken");

        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        // 2. Get current user
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .get("/api/auth/me")
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
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().response();

        String loginRefreshToken = loginResponse.jsonPath().getString("refreshToken");

        // 4. Refresh token
        Response refreshResponse = given()
                .contentType("application/json")
                .body("{\"refreshToken\": \"" + loginRefreshToken + "\"}")
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .extract().response();

        String newRefreshToken = refreshResponse.jsonPath().getString("refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(loginRefreshToken); // rotation

        // 5. Old refresh token should be revoked
        given()
                .contentType("application/json")
                .body("{\"refreshToken\": \"" + loginRefreshToken + "\"}")
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);

        // 6. Logout
        given()
                .contentType("application/json")
                .body("{\"refreshToken\": \"" + newRefreshToken + "\"}")
                .when()
                .post("/api/auth/logout")
                .then()
                .statusCode(200)
                .body("message", containsString("Logged out"));

        // 7. Revoked token should fail
        given()
                .contentType("application/json")
                .body("{\"refreshToken\": \"" + newRefreshToken + "\"}")
                .when()
                .post("/api/auth/refresh")
                .then()
                .statusCode(401);
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

        given().contentType("application/json").body(body).post("/api/auth/register")
                .then().statusCode(201);

        given().contentType("application/json").body(body).post("/api/auth/register")
                .then().statusCode(409);
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
                .post("/api/auth/register")
                .then().statusCode(201);

        // Login with wrong password
        given()
                .contentType("application/json")
                .body("{\"email\": \"wrongpass@example.com\", \"password\": \"badpass\"}")
                .post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void forgotPassword_shouldAlwaysReturn200() {
        given()
                .contentType("application/json")
                .body("{\"email\": \"doesnotexist@example.com\"}")
                .post("/api/auth/forgot-password")
                .then().statusCode(200)
                .body("message", notNullValue());
    }

    @Test
    void protectedEndpoint_shouldReturn403_whenNoToken() {
        given()
                .when()
                .get("/api/auth/me")
                .then()
                .statusCode(403);
    }
}
