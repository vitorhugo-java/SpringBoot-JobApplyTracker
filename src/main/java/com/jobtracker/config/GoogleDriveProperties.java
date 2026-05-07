package com.jobtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GoogleDriveProperties {

    private static final List<String> DEFAULT_SCOPES = List.of("https://www.googleapis.com/auth/drive");

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String oauthCompleteUrl;
    private final String authorizationUri;
    private final String tokenUri;
    private final List<String> scopes;

    public GoogleDriveProperties(
            @Value("${app.google-drive.client-id:}") String clientId,
            @Value("${app.google-drive.client-secret:}") String clientSecret,
            @Value("${app.google-drive.redirect-uri:}") String redirectUri,
            @Value("${app.google-drive.oauth-complete-url:}") String oauthCompleteUrl,
            @Value("${app.google-drive.authorization-uri:https://accounts.google.com/o/oauth2/v2/auth}") String authorizationUri,
            @Value("${app.google-drive.token-uri:https://oauth2.googleapis.com/token}") String tokenUri
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.oauthCompleteUrl = oauthCompleteUrl;
        this.authorizationUri = authorizationUri;
        this.tokenUri = tokenUri;
        this.scopes = DEFAULT_SCOPES;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getOauthCompleteUrl() {
        return oauthCompleteUrl;
    }

    public String getAuthorizationUri() {
        return authorizationUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public String getScopeValue() {
        return String.join(" ", scopes);
    }

    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri) && hasText(oauthCompleteUrl);
    }

    public void validateConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Google Drive integration is not configured on the server");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
