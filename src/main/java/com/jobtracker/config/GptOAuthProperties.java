package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "app.gpt-oauth")
public class GptOAuthProperties {

    private String clientId = "";
    private String clientSecret = "";
    private List<String> redirectUris = new ArrayList<>();
    private List<String> scopes = new ArrayList<>();
    private String issuer;
    private long authorizationCodeExpirationSeconds = 300;
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

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAuthorizationCodeExpirationSeconds() {
        return authorizationCodeExpirationSeconds;
    }

    public void setAuthorizationCodeExpirationSeconds(long authorizationCodeExpirationSeconds) {
        this.authorizationCodeExpirationSeconds = authorizationCodeExpirationSeconds;
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
        return hasText(clientId) && hasText(clientSecret) && !getRedirectUris().isEmpty() && !getScopes().isEmpty();
    }

    public void validateConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("GPT OAuth integration is not configured on the server");
        }
    }

    public String normalizedIssuer() {
        if (issuer == null || issuer.isBlank()) {
            return issuer;
        }
        return issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    public Duration getAuthorizationCodeTimeToLive() {
        return Duration.ofSeconds(authorizationCodeExpirationSeconds);
    }

    public Duration getAccessTokenTimeToLive() {
        return Duration.ofSeconds(accessTokenExpirationSeconds);
    }

    public Duration getRefreshTokenTimeToLive() {
        return Duration.ofSeconds(refreshTokenExpirationSeconds);
    }

    public Set<String> scopeSet() {
        return new LinkedHashSet<>(getScopes());
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
