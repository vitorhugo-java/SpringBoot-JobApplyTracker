package com.jobtracker.service;

import java.time.LocalDateTime;
import java.util.Optional;

public interface GoogleDriveApiClient {

    String GOOGLE_DOC_MIME_TYPE = "application/vnd.google-apps.document";
    String GOOGLE_FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    String buildAuthorizationUrl(String state);

    OAuthTokens exchangeAuthorizationCode(String code);

    OAuthTokens refreshAccessToken(String refreshToken);

    GoogleDriveAccountProfile getCurrentAccount(String accessToken);

    DriveFileMetadata getFileMetadata(String accessToken, String fileId);

    Optional<DriveFileMetadata> findFolderByName(String accessToken, String parentFolderId, String folderName);

    DriveFileMetadata createFolder(String accessToken, String parentFolderId, String folderName);

    DriveFileMetadata copyGoogleDoc(String accessToken, String sourceFileId, String targetFolderId, String newName);

    record OAuthTokens(String accessToken, String refreshToken, LocalDateTime accessTokenExpiresAt, String scope) {}

    record GoogleDriveAccountProfile(String accountId, String emailAddress, String displayName) {}

    record DriveFileMetadata(String id, String name, String mimeType, String webViewLink) {}
}
