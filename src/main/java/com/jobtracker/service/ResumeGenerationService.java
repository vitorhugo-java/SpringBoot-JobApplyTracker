package com.jobtracker.service;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeGenerationService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(.*?)}}");

    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveProperties googleDriveProperties;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveBaseResumeRepository baseResumeRepository;
    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public ResumeGenerationService(GoogleDriveApiClient googleDriveApiClient,
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

    @Transactional
    public ResumePlaceholderDetectionResponse detectPlaceholders(UUID applicationId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        GoogleDriveBaseResume baseResume = getBaseResume(applicationId, userId);
        String documentText = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), baseResume.getGoogleFileId());

        return new ResumePlaceholderDetectionResponse(
                baseResume.getId(),
                detectPlaceholders(documentText)
        );
    }

    @Transactional
    public BaseResumeContentResponse getBaseResumeContent(UUID resumeId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        GoogleDriveBaseResume baseResume = getBaseResume(resumeId, userId);
        String content = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), baseResume.getGoogleFileId());
        connectionRepository.save(connection);
        return new BaseResumeContentResponse(
                baseResume.getId(),
                baseResume.getDocumentName(),
                baseResume.getLanguage(),
                baseResume.isTemplate(),
                content
        );
    }

    @Transactional
    public GeneratedResumeContentResponse getGeneratedResumeContent(UUID applicationId) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        if (!StringUtils.hasText(application.getDriveResumeFileId())) {
            throw new BadRequestException("No generated resume found for this application");
        }

        String content = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), application.getDriveResumeFileId());
        connectionRepository.save(connection);

        return new GeneratedResumeContentResponse(
                application.getId(),
                application.getDriveResumeFileId(),
                application.getDriveResumeFileName(),
                content
        );
    }

    @Transactional
    public ResumePlaceholderResponse generateTemplateResume(UUID applicationId, ResumePlaceholderRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, userId).orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
        GoogleDriveBaseResume baseResume = getBaseResume(request.baseResumeId(), userId);

        if (!StringUtils.hasText(connection.getRootFolderId())) {
            throw new BadRequestException("Configure a Google Drive root folder before generating resumes");
        }

        GoogleDriveApiClient.DriveFileMetadata rootFolder =
                googleDriveApiClient.getFileMetadata(connection.getAccessToken(), connection.getRootFolderId());
        if (!GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE.equals(rootFolder.mimeType())) {
            throw new BadRequestException("Configured root folder is no longer a valid Google Drive folder");
        }
        connection.setRootFolderName(rootFolder.name());

        GoogleDriveApiClient.DriveFileMetadata vacancyFolder = resolveOrCreateVacancyFolder(connection, application, rootFolder.id(), userId);

        String copiedFileName = buildCopiedDocumentName(application, baseResume.getDocumentName());
        GoogleDriveApiClient.DriveFileMetadata copiedFile = googleDriveApiClient.copyGoogleDoc(
                connection.getAccessToken(),
                baseResume.getGoogleFileId(),
                vacancyFolder.id(),
                copiedFileName
        );

        Map<String, String> values = request.values() == null ? Map.of() : request.values();
        googleDriveApiClient.replaceGoogleDocPlaceholders(connection.getAccessToken(), copiedFile.id(), values);

        String copiedDocumentUrl = resolveDocumentLink(copiedFile);
        String copiedText = googleDriveApiClient.readGoogleDocText(connection.getAccessToken(), copiedFile.id());
        List<String> remainingPlaceholders = detectPlaceholders(copiedText);
        String pdfName = truncateFileName(sanitizeFileName(stripGoogleDocExtension(copiedFile.name()) + ".pdf"), 220);
        GoogleDriveApiClient.DriveFileMetadata pdfFile = googleDriveApiClient.exportGoogleDocAsPdf(
                connection.getAccessToken(),
                copiedFile.id(),
                vacancyFolder.id(),
                pdfName
        );
        LocalDateTime generatedAt = LocalDateTime.now();

        application.setDriveResumeFileId(copiedFile.id());
        application.setDriveResumeFileName(copiedFile.name());
        application.setDriveResumeDocumentUrl(copiedDocumentUrl);
        application.setDriveResumeGeneratedAt(generatedAt);

        connectionRepository.save(connection);
        applicationRepository.save(application);

        return new ResumePlaceholderResponse(
                application.getId(),
                baseResume.getId(),
                copiedFile.id(),
                pdfFile.id(),
                copiedDocumentUrl,
                resolveDocumentLink(pdfFile),
                values,
                remainingPlaceholders,
                generatedAt
        );
    }

    public List<String> detectPlaceholders(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        LinkedHashSet<String> placeholders = new LinkedHashSet<>();
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            if (StringUtils.hasText(placeholder)) {
                placeholders.add(placeholder);
            }
        }
        return List.copyOf(placeholders);
    }

    private GoogleDriveBaseResume getBaseResume(UUID baseResumeId, UUID userId) {
        return baseResumeRepository.findByIdAndConnectionUserId(baseResumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Base resume not found with id: " + baseResumeId));
    }

    private GoogleDriveConnection getConnectionWithFreshAccessToken() {
        if (!googleDriveProperties.isConfigured()) {
            throw new BadRequestException("Google Drive integration is not configured on the server");
        }
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

    private GoogleDriveApiClient.DriveFileMetadata resolveOrCreateVacancyFolder(
            GoogleDriveConnection connection,
            JobApplication application,
            String rootFolderId,
            UUID userId) {

        if (StringUtils.hasText(application.getDriveVacancyFolderId())) {
            return googleDriveApiClient.getFileMetadata(connection.getAccessToken(), application.getDriveVacancyFolderId());
        }

        String vacancyFolderName = buildVacancyFolderName(application);
        GoogleDriveApiClient.DriveFileMetadata folder = googleDriveApiClient
                .findFolderByName(connection.getAccessToken(), rootFolderId, vacancyFolderName)
                .orElseGet(() -> googleDriveApiClient.createFolder(connection.getAccessToken(), rootFolderId, vacancyFolderName));

        int updated = applicationRepository.setDriveVacancyFolderIdIfAbsent(application.getId(), folder.id());
        if (updated == 0) {
            String winningFolderId = applicationRepository.findByIdAndUserId(application.getId(), userId)
                    .map(JobApplication::getDriveVacancyFolderId)
                    .filter(StringUtils::hasText)
                    .orElse(folder.id());
            if (!winningFolderId.equals(folder.id())) {
                folder = googleDriveApiClient.getFileMetadata(connection.getAccessToken(), winningFolderId);
            }
        }

        return folder;
    }

    private String buildVacancyFolderName(JobApplication application) {
        String suffix = " - APP-" + application.getId();
        String rawBase = firstNonBlank(application.getVacancyName(), application.getOrganization(), "Application");
        String truncatedBase;
        if (rawBase != null) {
            truncatedBase = truncateFileName(sanitizeFileName(rawBase), 180 - suffix.length());
        } else throw new IllegalStateException("Vacancy name, organization and application id are all blank for application id: " + application.getId());
        return truncatedBase + suffix;
    }

    private String buildCopiedDocumentName(JobApplication application, String baseResumeName) {
        String vacancyName = firstNonBlank(application.getVacancyName(), application.getOrganization(), "Application");
        String prefix = "APP-" + application.getId() + " - " + vacancyName;
        return truncateFileName(sanitizeFileName(prefix + " - " + baseResumeName), 220);
    }

    private String stripGoogleDocExtension(String value) {
        return value == null ? "resume" : value.replaceFirst("(?i)\\.gdoc$", "");
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

    public record GeneratedResumeContentResponse(UUID applicationId, String resumeFileId, String fileName, String content) {}

}
