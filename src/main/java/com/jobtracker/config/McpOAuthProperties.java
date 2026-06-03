package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.mcp-oauth")
public class McpOAuthProperties {

    private String clientId = "";
    private String clientSecret = "";
    private List<String> redirectUris = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();
    private long accessTokenExpirationSeconds = 900;
    private long refreshTokenExpirationSeconds = 2592000;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public List<String> getRedirectUris() {
        return sanitizeList(redirectUris);
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getScopes() {
        return sanitizeList(scopes);
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public void setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) {
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }

    public void setRefreshTokenExpirationSeconds(long refreshTokenExpirationSeconds) {
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
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
