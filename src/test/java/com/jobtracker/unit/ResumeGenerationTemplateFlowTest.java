package com.jobtracker.unit;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.entity.GoogleDriveBaseResume;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.GoogleDriveBaseResumeRepository;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.service.GoogleDriveApiClient;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeGenerationTemplateFlowTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BASE_RESUME_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID APPLICATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock private GoogleDriveApiClient googleDriveApiClient;
    @Mock private GoogleDriveConnectionRepository connectionRepository;
    @Mock private GoogleDriveBaseResumeRepository baseResumeRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private SecurityUtils securityUtils;

    @Test
    void generateResume_shouldReplaceSpacedAndUnspacedPlaceholdersFromTemplateContent() {
        ResumeGenerationService service = new ResumeGenerationService(
                googleDriveApiClient,
                new GoogleDriveProperties("client", "secret", "cb", "frontend", "auth", "token"),
                connectionRepository,
                baseResumeRepository,
                applicationRepository,
                securityUtils
        );

        User user = new User();
        user.setId(USER_ID);

        GoogleDriveConnection connection = new GoogleDriveConnection();
        connection.setUser(user);
        connection.setAccessToken("access-token");
        connection.setRefreshToken("refresh-token");
        connection.setAccessTokenExpiresAt(LocalDateTime.now().plusHours(1));
        connection.setRootFolderId("root-folder-id");

        GoogleDriveBaseResume baseResume = new GoogleDriveBaseResume();
        baseResume.setId(BASE_RESUME_ID);
        baseResume.setConnection(connection);
        baseResume.setGoogleFileId("base-doc-id");
        baseResume.setDocumentName("Base Resume");

        JobApplication application = new JobApplication();
        application.setId(APPLICATION_ID);
        application.setUser(user);
        application.setVacancyName("Backend Engineer");
        application.setOrganization("Acme");
        application.setApplicationDate(LocalDate.now());
        application.setDriveVacancyFolderId("vacancy-folder-id");

        when(securityUtils.getCurrentUserId()).thenReturn(USER_ID);
        when(connectionRepository.findByUserId(USER_ID)).thenReturn(Optional.of(connection));
        when(applicationRepository.findByIdAndUserId(APPLICATION_ID, USER_ID)).thenReturn(Optional.of(application));
        when(baseResumeRepository.findByIdAndConnectionUserId(BASE_RESUME_ID, USER_ID)).thenReturn(Optional.of(baseResume));
        when(googleDriveApiClient.getFileMetadata("access-token", "root-folder-id"))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "root-folder-id",
                        "Root",
                        GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/root-folder-id"
                ));
        when(googleDriveApiClient.getFileMetadata("access-token", "vacancy-folder-id"))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "vacancy-folder-id",
                        "Vacancy",
                        GoogleDriveApiClient.GOOGLE_FOLDER_MIME_TYPE,
                        "https://drive.google.com/drive/folders/vacancy-folder-id"
                ));
        when(googleDriveApiClient.copyGoogleDoc(eq("access-token"), eq("base-doc-id"), eq("vacancy-folder-id"), any()))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "copied-doc-id",
                        "Copied Resume",
                        GoogleDriveApiClient.GOOGLE_DOC_MIME_TYPE,
                        "https://docs.google.com/document/d/copied-doc-id/edit"
                ));
        when(googleDriveApiClient.readGoogleDocText("access-token", "copied-doc-id"))
                .thenReturn("{{ SUMMARY }}\n{{SKILLS}}\n{{UNMAPPED}}")
                .thenReturn("Senior Java Engineer\nSpring Boot, PostgreSQL\n{{UNMAPPED}}");
        when(googleDriveApiClient.exportGoogleDocAsPdf(eq("access-token"), eq("copied-doc-id"), eq("vacancy-folder-id"), any()))
                .thenReturn(new GoogleDriveApiClient.DriveFileMetadata(
                        "pdf-id",
                        "Resume.pdf",
                        "application/pdf",
                        "https://drive.google.com/file/d/pdf-id/view"
                ));

        service.generateResume(APPLICATION_ID, new ResumePlaceholderRequest(
                BASE_RESUME_ID,
                Map.of(
                        "SUMMARY", "Senior Java Engineer",
                        "SKILLS", "Spring Boot, PostgreSQL"
                )
        ));

        ArgumentCaptor<Map<String, String>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(googleDriveApiClient).replaceGoogleDocPlaceholders(eq("access-token"), eq("copied-doc-id"), valuesCaptor.capture());
        assertThat(valuesCaptor.getValue()).containsEntry("{{ SUMMARY }}", "Senior Java Engineer");
        assertThat(valuesCaptor.getValue()).containsEntry("{{SKILLS}}", "Spring Boot, PostgreSQL");
        assertThat(valuesCaptor.getValue()).doesNotContainKeys("SUMMARY", "SKILLS");
    }
}
