package com.jobtracker.e2e;

import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.RefreshTokenRepository;
import com.jobtracker.repository.UserRepository;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

class ApplicationE2ETest extends AbstractE2ETest {

    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ApplicationRepository applicationRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        Response register = given()
                .contentType("application/json")
                .body("""
                        {
                          "name": "App E2E User",
                          "email": "appe2e@example.com",
                          "password": "pass1234",
                          "confirmPassword": "pass1234"
                        }
                        """)
                .post("/api/v1/auth/register")
                .then().statusCode(201).extract().response();

        accessToken = register.jsonPath().getString("accessToken");
    }

    @Test
    void fullApplicationCrud_createFetchUpdateDelete() {
        String applicationDate = LocalDate.now().minusDays(1).toString();

        // 1. Create application
        Response createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {
                          "vacancyName": "Software Engineer",
                          "recruiterName": "Jane Recruiter",
                          "vacancyOpenedBy": "HR",
                          "vacancyLink": "https://jobs.example.com/se",
                          "applicationDate": "%s",
                          "rhAcceptedConnection": false,
                          "interviewScheduled": false,
                          "status": "RH",
                          "recruiterDmReminderEnabled": false
                        }
                        """.formatted(applicationDate))
                .post("/api/applications")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("vacancyName", equalTo("Software Engineer"))
                .body("status", equalTo("RH"))
                .extract().response();

        Long appId = createResponse.jsonPath().getLong("id");
        assertThat(appId).isNotNull();

        // 2. Fetch by ID
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/applications/{id}", appId)
                .then()
                .statusCode(200)
                .body("id", equalTo(appId.intValue()))
                .body("vacancyName", equalTo("Software Engineer"));

        // 3. Update application
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {
                          "vacancyName": "Senior Software Engineer",
                          "recruiterName": "Jane Recruiter",
                          "vacancyOpenedBy": "HR",
                          "vacancyLink": "https://jobs.example.com/sse",
                          "applicationDate": "%s",
                          "rhAcceptedConnection": true,
                          "interviewScheduled": false,
                          "status": "Fiz a RH - Aguardando Atualização",
                          "recruiterDmReminderEnabled": false
                        }
                        """.formatted(applicationDate))
                .put("/api/applications/{id}", appId)
                .then()
                .statusCode(200)
                .body("vacancyName", equalTo("Senior Software Engineer"))
                .body("status", equalTo("Fiz a RH - Aguardando Atualização"))
                .body("rhAcceptedConnection", equalTo(true));

        // 4. Update status
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("{\"status\": \"Teste Técnico\"}")
                .patch("/api/applications/{id}/status", appId)
                .then()
                .statusCode(200)
                .body("status", equalTo("Teste Técnico"));

        // 5. Update reminder
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("{\"recruiterDmReminderEnabled\": true}")
                .patch("/api/applications/{id}/reminder", appId)
                .then()
                .statusCode(200)
                .body("recruiterDmReminderEnabled", equalTo(true));

        // 6. List all
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/applications")
                .then()
                .statusCode(200)
                .body("content", hasSize(1))
                .body("totalElements", equalTo(1));

        // 7. Delete
        given()
                .header("Authorization", "Bearer " + accessToken)
                .delete("/api/applications/{id}", appId)
                .then()
                .statusCode(200)
                .body("message", equalTo("Application deleted successfully"));

        // 8. Verify deleted
        given()
                .header("Authorization", "Bearer " + accessToken)
                .get("/api/applications/{id}", appId)
                .then()
                .statusCode(404);
    }

    @Test
    void getAll_withFilter_shouldReturnFilteredResults() {
        String applicationDate = LocalDate.now().minusDays(1).toString();

        // Create two apps
        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"vacancyName": "App RH", "recruiterName": "Recruiter A", "vacancyOpenedBy": "HR",
                         "vacancyLink": "https://example.com/a", "applicationDate": "%s",
                         "rhAcceptedConnection": false, "interviewScheduled": false,
                         "status": "RH", "recruiterDmReminderEnabled": false}
                        """.formatted(applicationDate))
                .post("/api/applications").then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"vacancyName": "App Tecnico", "recruiterName": "Recruiter B", "vacancyOpenedBy": "Tech",
                         "vacancyLink": "https://example.com/b", "applicationDate": "%s",
                         "rhAcceptedConnection": false, "interviewScheduled": true,
                         "status": "Teste Técnico", "recruiterDmReminderEnabled": false}
                        """.formatted(applicationDate))
                .post("/api/applications").then().statusCode(201);

        // Filter by status
        given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("status", "RH")
                .get("/api/applications")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].vacancyName", equalTo("App RH"));

        // Filter by interviewScheduled
        given()
                .header("Authorization", "Bearer " + accessToken)
                .queryParam("interviewScheduled", true)
                .get("/api/applications")
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].vacancyName", equalTo("App Tecnico"));
    }

    @Test
    void createApplication_withoutAuth_shouldReturn403() {
        given()
                .contentType("application/json")
                .body("""
                        {"vacancyName": "No Auth", "vacancyOpenedBy": "HR",
                         "applicationDate": "2024-01-01", "rhAcceptedConnection": false,
                         "interviewScheduled": false, "status": "RH",
                         "recruiterDmReminderEnabled": false}
                        """)
                .post("/api/applications")
                .then()
                .statusCode(403);
    }

    @Test
    void getById_shouldReturn404_whenBelongsToAnotherUser() {
        // Create app for user 1
        String applicationDate = LocalDate.now().minusDays(1).toString();
        Response createResponse = given()
                .header("Authorization", "Bearer " + accessToken)
                .contentType("application/json")
                .body("""
                        {"vacancyName": "Private App", "vacancyOpenedBy": "HR",
                         "vacancyLink": "https://example.com/p",
                         "applicationDate": "%s", "rhAcceptedConnection": false,
                         "interviewScheduled": false, "status": "RH",
                         "recruiterDmReminderEnabled": false}
                        """.formatted(applicationDate))
                .post("/api/applications")
                .then().statusCode(201).extract().response();

        Long appId = createResponse.jsonPath().getLong("id");

        // Register and login as user 2
        given()
                .contentType("application/json")
                .body("""
                        {"name": "Other User", "email": "other@example.com",
                         "password": "pass1234", "confirmPassword": "pass1234"}
                        """)
                .post("/api/v1/auth/register");

        Response loginResp = given()
                .contentType("application/json")
                .body("{\"email\": \"other@example.com\", \"password\": \"pass1234\"}")
                .post("/api/v1/auth/login")
                .then().statusCode(200).extract().response();

        String otherToken = loginResp.jsonPath().getString("accessToken");

        // User 2 cannot access user 1's app
        given()
                .header("Authorization", "Bearer " + otherToken)
                .get("/api/applications/{id}", appId)
                .then()
                .statusCode(404);
    }
}
