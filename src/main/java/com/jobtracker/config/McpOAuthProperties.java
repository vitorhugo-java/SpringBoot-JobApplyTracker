package com.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.mcp-oauth")
public class McpOAuthProperties {

    @Value("${MCP_CLIENT_ID:}")
    private String clientId;

    @Value("${MCP_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("#{'${MCP_REDIRECT_URIS:}'.split(',')}")
    private List<String> redirectUris;

    @Value("#{'${OPENAI_GPT_SCOPES:}'.split(',')}")
    private List<String> scopes;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationSeconds;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationSeconds;

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public List<String> getRedirectUris() {
        return sanitizeList(redirectUris);
    }

    public List<String> getScopes() {
        return sanitizeList(scopes);
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public boolean isConfigured() {
        return hasText(clientId) && !getRedirectUris().isEmpty() && !getScopes().isEmpty();
    }

    public Duration getAccessTokenTimeToLive() {
        return Duration.ofSeconds(accessTokenExpirationSeconds);
    }

    public Duration getRefreshTokenTimeToLive() {
        return Duration.ofSeconds(refreshTokenExpirationSeconds);
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
