package com.jobtracker.service;

import com.google.api.services.drive.Drive;
import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class GoogleDriveGeneratedResumeDownloadService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveGeneratedResumeDownloadService.class);

    private static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String PDF_MIME_TYPE = "application/pdf";

    private final DriveClientFactory driveClientFactory;
    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveProperties googleDriveProperties;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveBaseResumeRepository baseResumeRepository;
    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public GoogleDriveGeneratedResumeDownloadService(DriveClientFactory driveClientFactory,
                                                     GoogleDriveApiClient googleDriveApiClient,
                                                     GoogleDriveProperties googleDriveProperties,
                                                     GoogleDriveConnectionRepository connectionRepository,
                                                     GoogleDriveBaseResumeRepository baseResumeRepository,
                                                     ApplicationRepository applicationRepository,
                                                     SecurityUtils securityUtils) {
        this.driveClientFactory = driveClientFactory;
        this.googleDriveApiClient = googleDriveApiClient;
        this.googleDriveProperties = googleDriveProperties;
        this.connectionRepository = connectionRepository;
        this.baseResumeRepository = baseResumeRepository;
        this.applicationRepository = applicationRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public DownloadedFile downloadAsDocx(UUID applicationId) {
        return downloadApplication(applicationId, DOCX_MIME_TYPE, "docx");
    }

    @Transactional
    public DownloadedFile downloadAsPdf(UUID applicationId) {
        return downloadApplication(applicationId, PDF_MIME_TYPE, "pdf");
    }

    @Transactional
    public DownloadedFile downloadBaseResumeAsDocx(UUID baseResumeId) {
        return downloadBaseResume(baseResumeId, DOCX_MIME_TYPE, "docx");
    }

    @Transactional
    public DownloadedFile downloadBaseResumeAsPdf(UUID baseResumeId) {
        return downloadBaseResume(baseResumeId, PDF_MIME_TYPE, "pdf");
    }

    private DownloadedFile downloadApplication(UUID applicationId, String exportMimeType, String extension) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        if (!StringUtils.hasText(application.getDriveResumeFileId())) {
            throw new BadRequestException("Generate a resume first before downloading it");
        }

        byte[] content = exportDocument(connection.getAccessToken(), application.getDriveResumeFileId(), exportMimeType);
        String fileName = buildDownloadFileName(
                firstNonBlank(application.getDriveResumeFileName(), application.getVacancyName(), application.getOrganization(), "application-resume"),
                extension
        );

        return new DownloadedFile(fileName, exportMimeType, content);
    }

    private DownloadedFile downloadBaseResume(UUID baseResumeId, String exportMimeType, String extension) {
        UUID userId = securityUtils.getCurrentUserId();
        GoogleDriveConnection connection = getConnectionWithFreshAccessToken();

        GoogleDriveBaseResume baseResume = baseResumeRepository
                .findByIdAndConnectionUserId(baseResumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Base resume not found with id: " + baseResumeId));

        byte[] content = exportDocument(connection.getAccessToken(), baseResume.getGoogleFileId(), exportMimeType);
        String fileName = buildDownloadFileName(baseResume.getDocumentName(), extension);

        return new DownloadedFile(fileName, exportMimeType, content);
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

    private byte[] exportDocument(String accessToken, String documentId, String mimeType) {
        Drive drive = driveClientFactory.create(accessToken);

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            drive.files().export(documentId, mimeType).executeMediaAndDownloadTo(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            log.error("event=GOOGLE_DRIVE_EXPORT_ERROR documentId={} mimeType={} message={}", documentId, mimeType, ex.getMessage(), ex);
            throw new BadRequestException("Failed to export generated resume as " + mimeType);
        }
    }

    private String buildDownloadFileName(String baseName, String extension) {
        String sanitized = sanitizeFileName(firstNonBlank(baseName, "resume"));
        int maxLength = Math.max(1, 220 - (extension.length() + 1));
        String truncated = sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength).trim();
        return truncated + "." + extension;
    }

    private String sanitizeFileName(String value) {
        return value.replaceAll("[\\/:*?\"<>|]+", "-").replaceAll("\\s+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "application-resume";
    }

    public record DownloadedFile(String fileName, String contentType, byte[] content) {}
}