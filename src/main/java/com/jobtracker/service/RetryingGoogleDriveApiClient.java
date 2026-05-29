package com.jobtracker.service;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Primary
public class RetryingGoogleDriveApiClient implements GoogleDriveApiClient {

    private static final Logger log = LoggerFactory.getLogger(RetryingGoogleDriveApiClient.class);

    private final SdkGoogleDriveApiClient delegate;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveProperties googleDriveProperties;
    private final SecurityUtils securityUtils;

    public RetryingGoogleDriveApiClient(SdkGoogleDriveApiClient delegate,
                                        GoogleDriveConnectionRepository connectionRepository,
                                        GoogleDriveProperties googleDriveProperties,
                                        SecurityUtils securityUtils) {
        this.delegate = delegate;
        this.connectionRepository = connectionRepository;
        this.googleDriveProperties = googleDriveProperties;
        this.securityUtils = securityUtils;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return delegate.buildAuthorizationUrl(state);
    }

    @Override
    public OAuthTokens exchangeAuthorizationCode(String code) {
        return delegate.exchangeAuthorizationCode(code);
    }

    @Override
    public OAuthTokens refreshAccessToken(String refreshToken) {
        return delegate.refreshAccessToken(refreshToken);
    }

    @Override
    public GoogleDriveAccountProfile getCurrentAccount(String accessToken) {
        return delegate.getCurrentAccount(accessToken);
    }

    @Override
    public DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        return delegate.getFileMetadata(accessToken, fileId);
    }

    @Override
    public Optional<DriveFileMetadata> findFolderByName(String accessToken, String parentFolderId, String folderName) {
        return delegate.findFolderByName(accessToken, parentFolderId, folderName);
    }

    @Override
    public DriveFileMetadata createFolder(String accessToken, String parentFolderId, String folderName) {
        return delegate.createFolder(accessToken, parentFolderId, folderName);
    }

    @Override
    public DriveFileMetadata copyGoogleDoc(String accessToken, String sourceFileId, String targetFolderId, String newName) {
        return delegate.copyGoogleDoc(accessToken, sourceFileId, targetFolderId, newName);
    }

    @Override
    public String readGoogleDocText(String accessToken, String documentId) {
        try {
            return delegate.readGoogleDocText(accessToken, documentId);
        } catch (BadRequestException ex) {
            if (!isAuthenticationError(ex)) {
                throw ex;
            }

            log.warn("event=GOOGLE_DOCS_AUTH_RETRY documentId={} message={}", documentId, ex.getMessage());
            GoogleDriveConnection connection = refreshCurrentUserConnection();
            return delegate.readGoogleDocText(connection.getAccessToken(), documentId);
        }
    }

    @Override
    public void replaceGoogleDocPlaceholders(String accessToken, String documentId, Map<String, String> values) {
        delegate.replaceGoogleDocPlaceholders(accessToken, documentId, values);
    }

    @Override
    public DriveFileMetadata exportGoogleDocAsPdf(String accessToken, String documentId, String targetFolderId, String pdfName) {
        return delegate.exportGoogleDocAsPdf(accessToken, documentId, targetFolderId, pdfName);
    }

    @Transactional
    private GoogleDriveConnection refreshCurrentUserConnection() {
        if (!googleDriveProperties.isConfigured()) {
            throw new BadRequestException("Google Drive integration is not configured on the server");
        }

        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = connectionRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("Google Drive is not connected for the current user"));

        OAuthTokens refreshed = delegate.refreshAccessToken(connection.getRefreshToken());
        connection.setAccessToken(refreshed.accessToken());
        connection.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
        if (StringUtils.hasText(refreshed.scope())) {
            connection.setGrantedScopes(refreshed.scope());
        }
        return connectionRepository.save(connection);
    }

    private boolean isAuthenticationError(BadRequestException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }

        String normalized = message.toLowerCase();
        return normalized.contains("status 401")
                || normalized.contains("invalid authentication credentials")
                || normalized.contains("invalid credentials")
                || normalized.contains("insufficient authentication scopes");
    }
}
