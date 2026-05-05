package com.jobtracker.service;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.dto.gdrive.*;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleDriveService {

    private static final Pattern GOOGLE_DRIVE_PATH_ID_PATTERN = Pattern.compile("/(?:d|folders)/([a-zA-Z0-9_-]{10,})");
    private static final Pattern GOOGLE_DRIVE_QUERY_ID_PATTERN = Pattern.compile("[?&]id=([a-zA-Z0-9_-]{10,})");

    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveProperties googleDriveProperties;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveBaseResumeRepository baseResumeRepository;
    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public GoogleDriveService(GoogleDriveApiClient googleDriveApiClient,
                              GoogleDriveProperties googleDriveProperties,
                              GoogleDriveConnectionRepository connectionRepository,
                              GoogleDriveBaseResumeRepository baseResumeRepository,
                              ApplicationRepository applicationRepository,
                              SecurityUtils securityUtils) {
        this.googleDriveApiClient = googleDriveApiClient;
        this.googleDriveProperties = googleDriveProperties;
        this.connectionRepository = connectionRepository;
        this.baseResumeRepository = baseResumeRepository;
        this.applicationRepository = applicationRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public GoogleDriveStatusResponse getStatus() {
        if (!googleDriveProperties.isConfigured()) {
            return new GoogleDriveStatusResponse(false, false, null, null, null, null, null, null, List.of());
        }

        Optional<GoogleDriveConnection> connectionOptional = connectionRepository.findByUserId(securityUtils.getCurrentUserId());
        if (connectionOptional.isEmpty()) {
            return new GoogleDriveStatusResponse(true, false, null, null, null, null, null, null, List.of());
        }

        return toStatusResponse(connectionOptional.get());
    }

    @Transactional
    public GoogleDriveStatusResponse updateRootFolder(GoogleDriveRootFolderRequest request) {
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        String folderId = extractGoogleFileId(request.folderIdOrUrl());

        GoogleDriveApiClient.DriveFileMetadata folder = googleDriveApiClient.getFileMetadata(connection.getAccessToken(), folderId);
        if (!GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE.equals(folder.mimeType())) {
            throw new BadRequestException("Configured root folder must be a Google Drive folder");
        }

        connection.setRootFolderId(folder.id());
        connection.setRootFolderName(folder.name());
        connectionRepository.save(connection);
        return toStatusResponse(connection);
    }

    @Transactional
    public GoogleDriveBaseResumeResponse addBaseResume(GoogleDriveBaseResumeRequest request) {
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        String documentId = extractGoogleFileId(request.documentIdOrUrl());

        GoogleDriveApiClient.DriveFileMetadata file = googleDriveApiClient.getFileMetadata(connection.getAccessToken(), documentId);
        if (!GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE.equals(file.mimeType())) {
            throw new BadRequestException("Only Google Docs base resumes are supported");
        }

        GoogleDriveBaseResume resume = baseResumeRepository.findAllByConnectionIdOrderByCreatedAtAsc(connection.getId())
                .stream()
                .filter(existing -> existing.getGoogleFileId().equals(file.id()))
                .findFirst()
                .orElseGet(GoogleDriveBaseResume::new);

        resume.setConnection(connection);
        resume.setGoogleFileId(file.id());
        resume.setDocumentName(file.name());
        resume.setWebViewLink(resolveDocumentLink(file));
        GoogleDriveBaseResume saved = baseResumeRepository.save(resume);
        return toBaseResumeResponse(saved);
    }

    @Transactional
    public void deleteBaseResume(UUID baseResumeId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveBaseResume resume = baseResumeRepository.findByIdAndConnectionUserId(baseResumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Base resume not found with id: " + baseResumeId));
        baseResumeRepository.delete(resume);
    }

    @Transactional
    public GoogleDriveResumeCopyResponse copyBaseResumeToApplication(UUID applicationId, GoogleDriveResumeCopyRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        JobApplication application = applicationRepository.findByIdAndUserIdForUpdate(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        if (!StringUtils.hasText(connection.getRootFolderId())) {
            throw new BadRequestException("Configure a Google Drive root folder before copying resumes");
        }

        GoogleDriveBaseResume baseResume = baseResumeRepository.findByIdAndConnectionUserId(request.baseResumeId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Base resume not found with id: " + request.baseResumeId()));

        GoogleDriveApiClient.DriveFileMetadata rootFolder =
                googleDriveApiClient.getFileMetadata(connection.getAccessToken(), connection.getRootFolderId());
        if (!GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE.equals(rootFolder.mimeType())) {
            throw new BadRequestException("Configured root folder is no longer a valid Google Drive folder");
        }
        connection.setRootFolderName(rootFolder.name());

        String vacancyFolderName = buildVacancyFolderName(application);
        GoogleDriveApiClient.DriveFileMetadata vacancyFolder = googleDriveApiClient
                .findFolderByName(connection.getAccessToken(), rootFolder.id(), vacancyFolderName)
                .orElseGet(() -> googleDriveApiClient.createFolder(connection.getAccessToken(), rootFolder.id(), vacancyFolderName));

        String copiedFileName = buildCopiedDocumentName(application, baseResume.getDocumentName());
        GoogleDriveApiClient.DriveFileMetadata copiedFile = googleDriveApiClient.copyGoogleDoc(
                connection.getAccessToken(),
                baseResume.getGoogleFileId(),
                vacancyFolder.id(),
                copiedFileName
        );

        connectionRepository.save(connection);
        return new GoogleDriveResumeCopyResponse(
                application.getId(),
                baseResume.getId(),
                copiedFile.id(),
                copiedFile.name(),
                resolveDocumentLink(copiedFile),
                vacancyFolder.id(),
                vacancyFolder.name(),
                resolveFolderLink(vacancyFolder.id(), vacancyFolder.webViewLink())
        );
    }

    private GoogleDriveConnection getConnectionWithFreshAccessToken() {
        requireServerConfigured();
        GoogleDriveConnection connection = connectionRepository.findByUserId(securityUtils.getCurrentUserId())
                .orElseThrow(() -> new BadRequestException("Google Drive is not connected for the current user"));
        return refreshAccessTokenIfNeeded(connection);
    }

    private GoogleDriveConnection refreshAccessTokenIfNeeded(GoogleDriveConnection connection) {
        if (connection.getAccessTokenExpiresAt() != null
                && connection.getAccessTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            return connection;
        }

        GoogleDriveApiClient.OAuthTokens refreshed = googleDriveApiClient.refreshAccessToken(connection.getRefreshToken());
        connection.setAccessToken(refreshed.accessToken());
        connection.setAccessTokenExpiresAt(refreshed.accessTokenExpiresAt());
        if (StringUtils.hasText(refreshed.scope())) {
            connection.setGrantedScopes(refreshed.scope());
        }
        return connectionRepository.save(connection);
    }

    private GoogleDriveStatusResponse toStatusResponse(GoogleDriveConnection connection) {
        List<GoogleDriveBaseResumeResponse> resumes = baseResumeRepository.findAllByConnectionIdOrderByCreatedAtAsc(connection.getId())
                .stream()
                .sorted(Comparator.comparing(GoogleDriveBaseResume::getCreatedAt))
                .map(this::toBaseResumeResponse)
                .toList();
        return new GoogleDriveStatusResponse(
                true,
                true,
                connection.getGoogleEmail(),
                connection.getGoogleDisplayName(),
                connection.getGoogleAccountId(),
                connection.getRootFolderId(),
                connection.getRootFolderName(),
                connection.getConnectedAt(),
                resumes
        );
    }

    private GoogleDriveBaseResumeResponse toBaseResumeResponse(GoogleDriveBaseResume resume) {
        return new GoogleDriveBaseResumeResponse(
                resume.getId(),
                resume.getGoogleFileId(),
                resume.getDocumentName(),
                resume.getWebViewLink(),
                resume.getCreatedAt()
        );
    }

    private void requireServerConfigured() {
        if (!googleDriveProperties.isConfigured()) {
            throw new BadRequestException("Google Drive integration is not configured on the server");
        }
    }

    private String extractGoogleFileId(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw new BadRequestException("Google file or folder ID is required");
        }

        String trimmed = rawValue.trim();
        if (!trimmed.contains("/")) {
            return trimmed;
        }

        Matcher pathMatcher = GOOGLE_DRIVE_PATH_ID_PATTERN.matcher(trimmed);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }

        Matcher queryMatcher = GOOGLE_DRIVE_QUERY_ID_PATTERN.matcher(trimmed);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        throw new BadRequestException("Could not extract a Google file or folder ID from the provided value");
    }

    private String buildVacancyFolderName(JobApplication application) {
        String suffix = " - APP-" + application.getId().toString();
        String rawBase = firstNonBlank(application.getVacancyName(), application.getOrganization(), "Application");
        String truncatedBase = truncateFileName(sanitizeFileName(rawBase), 180 - suffix.length());
        return truncatedBase + suffix;
    }

    private String buildCopiedDocumentName(JobApplication application, String baseResumeName) {
        String vacancyName = firstNonBlank(application.getVacancyName(), application.getOrganization(), "Application");
        String prefix = "APP-" + application.getId() + " - " + vacancyName;
        return truncateFileName(sanitizeFileName(prefix + " - " + baseResumeName), 220);
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]+", "-").replaceAll("\\s+", " ").trim();
    }

    private String truncateFileName(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String resolveDocumentLink(GoogleDriveApiClient.DriveFileMetadata file) {
        return StringUtils.hasText(file.webViewLink())
                ? file.webViewLink()
                : "https://docs.google.com/document/d/" + file.id() + "/edit";
    }

    private String resolveFolderLink(String folderId, String webViewLink) {
        return StringUtils.hasText(webViewLink)
                ? webViewLink
                : "https://drive.google.com/drive/folders/" + folderId;
    }
}
