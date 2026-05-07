package com.jobtracker.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.User;
import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Google Drive API client backed by the official Google Drive Java SDK and Spring Security's
 * OAuth2 token exchange infrastructure.
 *
 * <ul>
 *   <li>All Drive file operations (getFileMetadata, findFolderByName, createFolder, copyGoogleDoc,
 *       getCurrentAccount) use the {@code google-api-services-drive} SDK via {@link DriveClientFactory}.
 *   <li>OAuth2 token exchange and refresh use Spring Security's
 *       {@link org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient}
 *       and {@link org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient},
 *       eliminating all manual HTTP token requests.
 *   <li>Google API errors are classified: 4xx responses become {@link BadRequestException} (HTTP
 *       400); 5xx and rate-limit (429) responses become {@link ServiceUnavailableException} (HTTP
 *       503) so that callers can distinguish retryable failures from invalid requests.
 * </ul>
 */
@Component
public class SdkGoogleDriveApiClient implements GoogleDriveApiClient {

    private static final Logger log = LoggerFactory.getLogger(SdkGoogleDriveApiClient.class);

    private final GoogleDriveProperties properties;
    private final ClientRegistration clientRegistration;
    private final DriveClientFactory driveClientFactory;
    private final OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient;
    private final OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient;

    public SdkGoogleDriveApiClient(
            GoogleDriveProperties properties,
            ClientRegistration clientRegistration,
            DriveClientFactory driveClientFactory,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeClient,
            OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenClient) {
        this.properties = properties;
        this.clientRegistration = clientRegistration;
        this.driveClientFactory = driveClientFactory;
        this.authorizationCodeClient = authorizationCodeClient;
        this.refreshTokenClient = refreshTokenClient;
    }

    // ── OAuth2 operations ────────────────────────────────────────────────────

    @Override
    public String buildAuthorizationUrl(String state) {
        properties.validateConfigured();
        return UriComponentsBuilder
                .fromUriString(clientRegistration.getProviderDetails().getAuthorizationUri())
                .queryParam("client_id", clientRegistration.getClientId())
                .queryParam("redirect_uri", clientRegistration.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", clientRegistration.getScopes()))
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    /**
     * Exchanges an authorization code for tokens using Spring's
     * {@link org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient}.
     * State validation is performed upstream by {@code GoogleDriveOAuthService} before calling
     * this method, so a placeholder value is used here to satisfy the exchange API contract.
     */
    @Override
    public OAuthTokens exchangeAuthorizationCode(String code) {
        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(clientRegistration.getClientId())
                .authorizationUri(clientRegistration.getProviderDetails().getAuthorizationUri())
                .redirectUri(clientRegistration.getRedirectUri())
                .scopes(clientRegistration.getScopes())
                .state("_")
                .build();
        OAuth2AuthorizationResponse authResponse = OAuth2AuthorizationResponse.success(code)
                .redirectUri(clientRegistration.getRedirectUri())
                .state("_")
                .build();
        try {
            OAuth2AccessTokenResponse response = authorizationCodeClient.getTokenResponse(
                    new OAuth2AuthorizationCodeGrantRequest(clientRegistration,
                            new OAuth2AuthorizationExchange(authRequest, authResponse)));
            if (response.getRefreshToken() == null) {
                throw new BadRequestException(
                        "Google OAuth did not return a refresh token. Reconnect and grant consent again.");
            }
            return toOAuthTokens(response, response.getRefreshToken().getTokenValue());
        } catch (OAuth2AuthorizationException ex) {
            throw translateOAuth2Exception(ex, "exchange authorization code");
        }
    }

    @Override
    public OAuthTokens refreshAccessToken(String refreshToken) {
        // A placeholder access token is required by the grant-request API; Google ignores it.
        OAuth2AccessToken placeholder = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "placeholder", null, null);
        try {
            OAuth2AccessTokenResponse response = refreshTokenClient.getTokenResponse(
                    new OAuth2RefreshTokenGrantRequest(clientRegistration, placeholder,
                            new OAuth2RefreshToken(refreshToken, null)));
            return toOAuthTokens(response, refreshToken);
        } catch (OAuth2AuthorizationException ex) {
            throw translateOAuth2Exception(ex, "refresh access token");
        }
    }

    // ── Drive file operations (SDK) ──────────────────────────────────────────

    @Override
    public GoogleDriveAccountProfile getCurrentAccount(String accessToken) {
        return executeDriveOp(accessToken, "read account", drive -> {
            About about = drive.about().get()
                    .setFields("user(emailAddress,displayName,permissionId)")
                    .execute();
            User user = about.getUser();
            return new GoogleDriveAccountProfile(
                    user.getPermissionId(),
                    user.getEmailAddress(),
                    user.getDisplayName());
        });
    }

    @Override
    public DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        return executeDriveOp(accessToken, "get file metadata", drive ->
                toDriveFileMetadata(drive.files().get(fileId)
                        .setSupportsAllDrives(true)
                        .setFields("id,name,mimeType,webViewLink")
                        .execute()));
    }

    @Override
    public Optional<DriveFileMetadata> findFolderByName(String accessToken, String parentFolderId, String folderName) {
        return executeDriveOp(accessToken, "find folder", drive -> {
            String escaped = folderName.replace("\\", "\\\\").replace("'", "\\'");
            String q = "mimeType='" + GOOGLE_FOLDER_MIME_TYPE + "' and trashed=false and '"
                    + parentFolderId + "' in parents and name='" + escaped + "'";
            FileList result = drive.files().list()
                    .setQ(q)
                    .setPageSize(1)
                    .setFields("files(id,name,mimeType,webViewLink)")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setCorpora("allDrives")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toDriveFileMetadata(files.get(0)));
        });
    }

    @Override
    public DriveFileMetadata createFolder(String accessToken, String parentFolderId, String folderName) {
        return executeDriveOp(accessToken, "create folder", drive -> {
            File metadata = new File()
                    .setName(folderName)
                    .setMimeType(GOOGLE_FOLDER_MIME_TYPE)
                    .setParents(List.of(parentFolderId));
            return toDriveFileMetadata(drive.files().create(metadata)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,mimeType,webViewLink")
                    .execute());
        });
    }

    @Override
    public DriveFileMetadata copyGoogleDoc(String accessToken, String sourceFileId, String targetFolderId, String newName) {
        return executeDriveOp(accessToken, "copy document", drive -> {
            File metadata = new File()
                    .setName(newName)
                    .setParents(List.of(targetFolderId));
            return toDriveFileMetadata(drive.files().copy(sourceFileId, metadata)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,mimeType,webViewLink")
                    .execute());
        });
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    @FunctionalInterface
    private interface DriveOperation<T> {
        T execute(Drive drive) throws IOException;
    }

    private <T> T executeDriveOp(String accessToken, String action, DriveOperation<T> op) {
        Drive drive = driveClientFactory.create(accessToken, null);
        try {
            return op.execute(drive);
        } catch (GoogleJsonResponseException ex) {
            throw translateDriveException(action, ex);
        } catch (IOException ex) {
            log.warn("event=GOOGLE_DRIVE_IO_ERROR action={} message={}", action, ex.getMessage());
            throw new ServiceUnavailableException("Google Drive is temporarily unavailable (" + action + ")");
        }
    }

    private RuntimeException translateDriveException(String action, GoogleJsonResponseException ex) {
        int status = ex.getStatusCode();
        String message = "Failed to " + action + " (status " + status + ")";
        if (status == 429 || status >= 500) {
            log.warn("event=GOOGLE_DRIVE_UPSTREAM_ERROR action={} status={}", action, status);
            return new ServiceUnavailableException(message);
        }
        log.warn("event=GOOGLE_DRIVE_CLIENT_ERROR action={} status={}", action, status);
        return new BadRequestException(message);
    }

    private RuntimeException translateOAuth2Exception(OAuth2AuthorizationException ex, String action) {
        String code = ex.getError().getErrorCode();
        log.warn("event=GOOGLE_OAUTH_ERROR action={} errorCode={}", action, code);
        if ("invalid_grant".equals(code) || "invalid_client".equals(code)) {
            return new BadRequestException("Google OAuth error during " + action + ": " + code);
        }
        return new ServiceUnavailableException("Google OAuth service unavailable during " + action);
    }

    private OAuthTokens toOAuthTokens(OAuth2AccessTokenResponse response, String refreshTokenValue) {
        OAuth2AccessToken accessToken = response.getAccessToken();
        Instant expiresAt = accessToken.getExpiresAt();
        LocalDateTime expiresAtLdt = expiresAt != null
                ? LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        Set<String> scopes = accessToken.getScopes();
        String scopeStr = (scopes == null || scopes.isEmpty()) ? null : String.join(" ", scopes);
        return new OAuthTokens(accessToken.getTokenValue(), refreshTokenValue, expiresAtLdt, scopeStr);
    }

    private DriveFileMetadata toDriveFileMetadata(File file) {
        return new DriveFileMetadata(
                file.getId(),
                file.getName(),
                file.getMimeType(),
                file.getWebViewLink());
    }
}
