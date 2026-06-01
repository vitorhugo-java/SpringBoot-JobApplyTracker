package com.jobtracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String GPT_OAUTH_SCHEME_NAME = "gptOAuth";
    private static final String CONTROLLER_PACKAGE = "com.jobtracker.controller";

    @Bean
    public OpenAPI openAPI(@Value("${app.api.base-url:https://jobapply-api.hugojava.dev}") String apiBaseUrl) {
        return new OpenAPI()
                .servers(List.of(new Server().url(apiBaseUrl).description("Production")))
                .info(new Info()
                        .title("JobApply API")
                        .description("API for tracking job applications")
                        .version("1.0.0"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(GPT_OAUTH_SCHEME_NAME, new SecurityScheme()
                                .name(GPT_OAUTH_SCHEME_NAME)
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows()
                                        .authorizationCode(new OAuthFlow()
                                                .authorizationUrl(apiBaseUrl + "/oauth2/authorize")
                                                .tokenUrl(apiBaseUrl + "/oauth2/token")
                                                .scopes(new Scopes()
                                                        .addString("read:profile", "Read the authenticated user's profile")
                                                        .addString("read:applications", "Read the authenticated user's applications")
                                                        .addString("write:applications", "Create or update the authenticated user's applications")
                                                        .addString("read:resume", "Read resume content for the authenticated user")
                                                        .addString("read:google-drive", "Read Google Drive integration status for the authenticated user")
                                                        .addString("read:metrics", "Read dashboard metrics for the authenticated user"))))));
    }

    @Bean
    public GroupedOpenApi applicationsOpenApi() {
        return GroupedOpenApi.builder()
                .group("applications")
                .displayName("Application API")
                .packagesToScan(CONTROLLER_PACKAGE)
                .pathsToMatch("/api/v1/applications/**", "/api/v1/applications")
                .build();
    }

    @Bean
    public GroupedOpenApi googleDriveOpenApi() {
        return GroupedOpenApi.builder()
                .group("google-drive")
                .displayName("Google Drive API")
                .packagesToScan(CONTROLLER_PACKAGE)
                .pathsToMatch("/api/v1/google-drive/**", "/api/v1/google-drive")
                .build();
    }

    @Bean
    public GroupedOpenApi gptOpenApi() {
        return GroupedOpenApi.builder()
                .group("gpt-actions")
                .displayName("GPT Actions API")
                .packagesToScan(CONTROLLER_PACKAGE)
                .pathsToMatch(
                        "/api/v1/auth/me",
                        "/api/v1/applications",
                        "/api/v1/applications/{id}",
                        "/api/v1/applications/{id}/status",
                        "/api/v1/google-drive/status",
                        "/api/v1/google-drive/base-resumes",
                        "/api/v1/google-drive/base-resumes/{resumeId}/content",
                        "/api/v1/google-drive/applications/{applicationId}/generated-resumes/content",
                        "/api/v1/google-drive/applications/{applicationId}/generated-resumes/pdf",
                        "/api/v1/google-drive/resume-placeholders"
                )
                .addOpenApiCustomizer(openApi -> {
                    openApi.setSecurity(List.of(new SecurityRequirement().addList(GPT_OAUTH_SCHEME_NAME)));
                    if (openApi.getComponents() != null && openApi.getComponents().getSecuritySchemes() != null) {
                        openApi.getComponents().getSecuritySchemes().remove(SECURITY_SCHEME_NAME);
                    }

                    var authMe = openApi.getPaths().get("/api/v1/auth/me");
                    if (authMe != null) {
                        authMe.setPut(null);
                    }

                    var baseResume = openApi.getPaths().get("/api/v1/google-drive/base-resumes");
                    if (baseResume != null) {
                        baseResume.put(null);
                    }

                    var application = openApi.getPaths().get("/api/v1/applications/{id}");
                    if (application != null) {
                        application.delete(null);
                    }
                })
                .build();
    }
}
