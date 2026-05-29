package com.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class GptOAuthProperties {

    private final String clientId;
    private final String clientSecret;
    private final List<String> redirectUris;
    private final List<String> scopes;
    private final String issuer;
    private final String audience;
    private final long authorizationCodeExpirationSeconds;
    private final long accessTokenExpirationSeconds;

    public GptOAuthProperties(
            @Value("${app.gpt-oauth.client-id:}") String clientId,
            @Value("${app.gpt-oauth.client-secret:}") String clientSecret,
            @Value("${app.gpt-oauth.redirect-uris:}") String redirectUrisValue,
            @Value("${app.gpt-oauth.scopes:read:profile,read:applications,write:applications,read:resume,read:google-drive,read:metrics}") String scopesValue,
            @Value("${app.gpt-oauth.issuer:${app.api.base-url:https://jobapply-api.hugojava.dev}}") String issuer,
            @Value("${app.gpt-oauth.audience:jobtracker-gpt-actions}") String audience,
            @Value("${app.gpt-oauth.authorization-code-expiration-seconds:300}") long authorizationCodeExpirationSeconds,
            @Value("${app.gpt-oauth.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUris = splitCsv(redirectUrisValue);
        this.scopes = splitCsv(scopesValue);
        this.issuer = issuer;
        this.audience = audience;
        this.authorizationCodeExpirationSeconds = authorizationCodeExpirationSeconds;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getAudience() {
        return audience;
    }

    public long getAuthorizationCodeExpirationSeconds() {
        return authorizationCodeExpirationSeconds;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret) && !redirectUris.isEmpty() && !scopes.isEmpty();
    }

    public void validateConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("GPT OAuth integration is not configured on the server");
        }
    }

    public boolean supportsRedirectUri(String redirectUri) {
        return redirectUris.contains(redirectUri);
    }

    public boolean supportsScopes(Set<String> requestedScopes) {
        return new LinkedHashSet<>(scopes).containsAll(requestedScopes);
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .distinct()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
