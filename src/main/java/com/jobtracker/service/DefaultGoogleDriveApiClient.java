package com.jobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.exception.BadRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class DefaultGoogleDriveApiClient implements GoogleDriveApiClient {

    private static final String DRIVE_API_BASE_URL = "https://www.googleapis.com/drive/v3";

    private final GoogleDriveProperties properties;
    private final RestClient restClient;

    public DefaultGoogleDriveApiClient(GoogleDriveProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        properties.validateConfigured();
        return UriComponentsBuilder.fromUriString(properties.getAuthorizationUri())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.getScopeValue())
                .queryParam("access_type", "offline")
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public OAuthTokens exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", code);
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("redirect_uri", properties.getRedirectUri());
        body.add("grant_type", "authorization_code");

        JsonNode response = postForm(properties.getTokenUri(), body, "exchange Google authorization code");
        String refreshToken = textValue(response, "refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Google OAuth did not return a refresh token. Reconnect and grant consent again.");
        }
        return toTokens(response, refreshToken);
    }

    @Override
    public OAuthTokens refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("refresh_token", refreshToken);
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("grant_type", "refresh_token");

        JsonNode response = postForm(properties.getTokenUri(), body, "refresh Google access token");
        return toTokens(response, refreshToken);
    }

    @Override
    public GoogleDriveAccountProfile getCurrentAccount(String accessToken) {
        String uri = UriComponentsBuilder.fromUriString(DRIVE_API_BASE_URL + "/about")
                .queryParam("fields", "user(emailAddress,displayName,permissionId)")
                .build()
                .encode()
                .toUriString();
        JsonNode response = getAuthorizedJson(uri, accessToken, "read Google Drive account");
        JsonNode userNode = response.path("user");
        return new GoogleDriveAccountProfile(
                userNode.path("permissionId").asText(),
                userNode.path("emailAddress").asText(),
                userNode.path("displayName").asText(null)
        );
    }

    @Override
    public DriveFileMetadata getFileMetadata(String accessToken, String fileId) {
        String uri = UriComponentsBuilder.fromUriString(DRIVE_API_BASE_URL + "/files/" + fileId)
                .queryParam("supportsAllDrives", "true")
                .queryParam("fields", "id,name,mimeType,webViewLink")
                .build()
                .encode()
                .toUriString();
        JsonNode response = getAuthorizedJson(uri, accessToken, "read Google Drive file metadata");
        return toDriveFileMetadata(response);
    }

    @Override
    public Optional<DriveFileMetadata> findFolderByName(String accessToken, String parentFolderId, String folderName) {
        String escapedFolderName = folderName.replace("\\", "\\\\").replace("'", "\\'");
        String query = "mimeType='" + GOOGLE_FOLDER_MIME_TYPE + "' and trashed=false and '" + parentFolderId
                + "' in parents and name='" + escapedFolderName + "'";
        String uri = UriComponentsBuilder.fromUriString(DRIVE_API_BASE_URL + "/files")
                .queryParam("supportsAllDrives", "true")
                .queryParam("includeItemsFromAllDrives", "true")
                .queryParam("corpora", "allDrives")
                .queryParam("q", query)
                .queryParam("pageSize", 1)
                .queryParam("fields", "files(id,name,mimeType,webViewLink)")
                .build()
                .encode()
                .toUriString();

        JsonNode response = getAuthorizedJson(uri, accessToken, "find Google Drive folder");
        JsonNode filesNode = response.path("files");
        if (!filesNode.isArray() || filesNode.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDriveFileMetadata(filesNode.get(0)));
    }

    @Override
    public DriveFileMetadata createFolder(String accessToken, String parentFolderId, String folderName) {
        JsonNode response = postAuthorizedJson(
                UriComponentsBuilder.fromUriString(DRIVE_API_BASE_URL + "/files")
                        .queryParam("supportsAllDrives", "true")
                        .queryParam("fields", "id,name,mimeType,webViewLink")
                        .build()
                        .encode()
                        .toUriString(),
                accessToken,
                new FolderCreateRequest(folderName, GOOGLE_FOLDER_MIME_TYPE, List.of(parentFolderId)),
                "create Google Drive folder"
        );
        return toDriveFileMetadata(response);
    }

    @Override
    public DriveFileMetadata copyGoogleDoc(String accessToken, String sourceFileId, String targetFolderId, String newName) {
        JsonNode response = postAuthorizedJson(
                UriComponentsBuilder.fromUriString(DRIVE_API_BASE_URL + "/files/" + sourceFileId + "/copy")
                        .queryParam("supportsAllDrives", "true")
                        .queryParam("fields", "id,name,mimeType,webViewLink")
                        .build()
                        .encode()
                        .toUriString(),
                accessToken,
                new FolderCreateRequest(newName, null, List.of(targetFolderId)),
                "copy Google Docs file"
        );
        return toDriveFileMetadata(response);
    }

    private JsonNode postForm(String uri, MultiValueMap<String, String> body, String action) {
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw googleApiException(action, ex);
        }
    }

    private JsonNode getAuthorizedJson(String uri, String accessToken, String action) {
        try {
            return restClient.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw googleApiException(action, ex);
        }
    }

    private JsonNode postAuthorizedJson(String uri, String accessToken, Object body, String action) {
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            throw googleApiException(action, ex);
        }
    }

    private OAuthTokens toTokens(JsonNode response, String refreshToken) {
        String accessToken = textValue(response, "access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadRequestException("Google OAuth response did not include an access token");
        }
        long expiresIn = response.path("expires_in").asLong(3600);
        String scope = textValue(response, "scope");
        return new OAuthTokens(accessToken, refreshToken, LocalDateTime.now().plus(Duration.ofSeconds(expiresIn)), scope);
    }

    private DriveFileMetadata toDriveFileMetadata(JsonNode node) {
        return new DriveFileMetadata(
                node.path("id").asText(),
                node.path("name").asText(),
                node.path("mimeType").asText(),
                node.path("webViewLink").asText(null)
        );
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        return child == null || child.isNull() ? null : child.asText();
    }

    private BadRequestException googleApiException(String action, RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        String message = responseBody;
        if (message == null || message.isBlank()) {
            message = ex.getStatusText();
        }
        throw new BadRequestException("Failed to " + action + ": " + message);
    }

    private record FolderCreateRequest(String name, String mimeType, List<String> parents) {}
}
