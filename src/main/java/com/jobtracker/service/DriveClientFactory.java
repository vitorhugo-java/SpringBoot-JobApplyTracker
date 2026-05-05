package com.jobtracker.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Factory that creates ready-to-use {@link Drive} SDK client instances from a stored access token.
 * The {@link HttpTransport} and {@link JsonFactory} are shared singletons (thread-safe) to avoid
 * the overhead of re-creating TLS contexts on every API call.
 */
@Component
public class DriveClientFactory {

    private static final String APPLICATION_NAME = "JobApplyTracker";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    public DriveClientFactory() throws GeneralSecurityException, IOException {
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    }

    /**
     * Creates a {@link Drive} client authenticated with the given access token.
     *
     * @param accessToken         the current OAuth2 access token
     * @param accessTokenExpiresAt expiry hint passed to the Google credentials (may be {@code null})
     * @return a configured {@link Drive} instance
     */
    public Drive create(String accessToken, LocalDateTime accessTokenExpiresAt) {
        Date expiryDate = accessTokenExpiresAt != null
                ? Date.from(accessTokenExpiresAt.toInstant(ZoneOffset.UTC))
                : null;
        AccessToken token = new AccessToken(accessToken, expiryDate);
        OAuth2Credentials credentials = OAuth2Credentials.create(token);

        HttpRequestInitializer requestInitializer = request -> {
            new HttpCredentialsAdapter(credentials).initialize(request);
            request.setConnectTimeout(CONNECT_TIMEOUT_MS);
            request.setReadTimeout(READ_TIMEOUT_MS);
        };

        return new Drive.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
