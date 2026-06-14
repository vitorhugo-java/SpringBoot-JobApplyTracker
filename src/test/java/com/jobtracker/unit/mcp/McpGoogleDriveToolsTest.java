package com.jobtracker.unit.mcp;

import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
import com.jobtracker.mcp.tools.McpGoogleDriveTools;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService.DownloadedFile;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpGoogleDriveToolsTest {

    @Mock
    private GoogleDriveService googleDriveService;

    @Mock
    private ResumeGenerationService resumeGenerationService;

    @Mock
    private GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService;

    @InjectMocks
    private McpGoogleDriveTools tools;

    @Test
    void copyResumeToApplication_parsesUuidsAndBuildsRequest() {
        UUID applicationId = UUID.randomUUID();
        UUID baseResumeId  = UUID.randomUUID();
        GoogleDriveResumeCopyResponse expected = mock(GoogleDriveResumeCopyResponse.class);
        ArgumentCaptor<GoogleDriveResumeCopyRequest> captor =
                ArgumentCaptor.forClass(GoogleDriveResumeCopyRequest.class);
        when(googleDriveService.copyBaseResumeToApplication(eq(applicationId), any()))
                .thenReturn(expected);

        GoogleDriveResumeCopyResponse result =
                tools.copyResumeToApplication(null, applicationId.toString(), baseResumeId.toString());

        assertThat(result).isSameAs(expected);
        verify(googleDriveService).copyBaseResumeToApplication(eq(applicationId), captor.capture());
        assertThat(captor.getValue().baseResumeId()).isEqualTo(baseResumeId);
    }

    @Test
    void detectResumePlaceholders_parsesUuidAndDelegates() {
        UUID baseResumeId = UUID.randomUUID();
        ResumePlaceholderDetectionResponse expected = mock(ResumePlaceholderDetectionResponse.class);
        when(resumeGenerationService.detectPlaceholders(baseResumeId)).thenReturn(expected);

        ResumePlaceholderDetectionResponse result = tools.detectResumePlaceholders(null, baseResumeId.toString());

        assertThat(result).isSameAs(expected);
        verify(resumeGenerationService).detectPlaceholders(baseResumeId);
    }

    @Test
    void generateResume_parsesUuidsAndBuildsRequest() {
        UUID applicationId = UUID.randomUUID();
        UUID baseResumeId = UUID.randomUUID();
        Map<String, String> values = Map.of("RESUMO", "Senior Java Developer");
        ResumePlaceholderResponse expected = mock(ResumePlaceholderResponse.class);
        ArgumentCaptor<ResumePlaceholderRequest> captor =
                ArgumentCaptor.forClass(ResumePlaceholderRequest.class);
        when(resumeGenerationService.generateTemplateResume(eq(applicationId), any()))
                .thenReturn(expected);

        ResumePlaceholderResponse result =
                tools.generateResume(null, applicationId.toString(), baseResumeId.toString(), values);

        assertThat(result).isSameAs(expected);
        verify(resumeGenerationService).generateTemplateResume(eq(applicationId), captor.capture());
        assertThat(captor.getValue().baseResumeId()).isEqualTo(baseResumeId);
        assertThat(captor.getValue().values()).isEqualTo(values);
    }

    @Test
    void downloadGeneratedResumePdf_returnsBase64EncodedPayload() {
        UUID applicationId = UUID.randomUUID();
        byte[] pdfBytes = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
        DownloadedFile file = new DownloadedFile("resume.pdf", "application/pdf", pdfBytes);
        when(generatedResumeDownloadService.downloadAsPdf(applicationId)).thenReturn(file);

        McpGoogleDriveTools.GeneratedResumePdf result =
                tools.downloadGeneratedResumePdf(null, applicationId.toString());

        assertThat(result.fileName()).isEqualTo("resume.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(Base64.getDecoder().decode(result.base64Content())).isEqualTo(pdfBytes);
        verify(generatedResumeDownloadService).downloadAsPdf(applicationId);
    }
}
