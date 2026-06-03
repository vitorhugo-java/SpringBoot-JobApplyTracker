package com.jobtracker.unit.mcp;

import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.GoogleDriveStatusResponse;
import com.jobtracker.mcp.tools.McpGoogleDriveTools;
import com.jobtracker.service.GoogleDriveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @InjectMocks
    private McpGoogleDriveTools tools;

    @Test
    void getDriveStatus_delegatesToService() {
        GoogleDriveStatusResponse expected = mock(GoogleDriveStatusResponse.class);
        when(googleDriveService.getStatus()).thenReturn(expected);

        GoogleDriveStatusResponse result = tools.getDriveStatus();

        assertThat(result).isSameAs(expected);
        verify(googleDriveService).getStatus();
    }

    @Test
    void listBaseResumes_delegatesToService() {
        List<BaseResumeResponse> expected = List.of(mock(BaseResumeResponse.class));
        when(googleDriveService.listBaseResumes()).thenReturn(expected);

        List<BaseResumeResponse> result = tools.listBaseResumes();

        assertThat(result).isSameAs(expected);
        verify(googleDriveService).listBaseResumes();
    }

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
                tools.copyResumeToApplication(applicationId.toString(), baseResumeId.toString());

        assertThat(result).isSameAs(expected);
        verify(googleDriveService).copyBaseResumeToApplication(eq(applicationId), captor.capture());
        assertThat(captor.getValue().baseResumeId()).isEqualTo(baseResumeId);
    }
}
