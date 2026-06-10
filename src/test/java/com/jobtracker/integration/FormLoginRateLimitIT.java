package com.jobtracker.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the rate limit on the authorization server form login (POST /login).
 *
 * The Spring AS login form is the entry door of the OAuth flow used by the MCP
 * connectors (Claude/ChatGPT) and is not covered by the AuthController limiters.
 * With Cloudflare Bot Fight Mode disabled (it blocked the MCP backends), this
 * filter is the brute-force protection for that endpoint.
 *
 * Runs against a real container: the login form lives in the authorization server
 * filter chain, whose request matching depends on the servlet path that MockMvc
 * does not populate.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "resilience4j.ratelimiter.instances.formLogin.limit-for-period=3",
                "resilience4j.ratelimiter.instances.formLogin.limit-refresh-period=1m",
                "resilience4j.ratelimiter.instances.formLogin.timeout-duration=0"
        })
@ActiveProfiles("test")
class FormLoginRateLimitIT {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void loginPost_overLimit_returns429() {
        // Under the limit: requests reach Spring Security (which rejects the bad
        // credentials/CSRF however it likes — anything but 429 is fine here).
        for (int i = 0; i < 3; i++) {
            Response response = given()
                    .contentType(ContentType.URLENC)
                    .formParam("username", "attacker@example.com")
                    .formParam("password", "guess-" + i)
                    .post("/login");
            assertThat(response.statusCode())
                    .as("request %d must not be rate limited yet", i + 1)
                    .isNotEqualTo(429);
        }

        // Over the limit: short-circuited with 429 before authentication runs.
        Response limited = given()
                .contentType(ContentType.URLENC)
                .formParam("username", "attacker@example.com")
                .formParam("password", "guess-final")
                .post("/login");
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.jsonPath().getInt("status")).isEqualTo(429);
        assertThat(limited.jsonPath().getString("message"))
                .isEqualTo("Too many requests. Please try again later.");
    }

    @Test
    void loginPage_get_isNotRateLimited() {
        // Only the POST is throttled; rendering the login page stays unlimited
        // (the OAuth redirect lands here for every connector authorization).
        for (int i = 0; i < 5; i++) {
            given().accept("text/html").get("/login").then().statusCode(200);
        }
    }
}
