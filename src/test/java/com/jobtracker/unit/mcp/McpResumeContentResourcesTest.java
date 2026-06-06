package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.resources.McpBaseResumeContentResource;
import com.jobtracker.mcp.resources.McpGeneratedResumeContentResource;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.service.ResumeGenerationService.GeneratedResumeContentResponse;
import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpResumeContentResourcesTest {

    @Mock
    private ResumeGenerationService resumeGenerationService;

    @InjectMocks
    private McpBaseResumeContentResource baseResumeContentResource;

    @InjectMocks
    private McpGeneratedResumeContentResource generatedResumeContentResource;

    @Test
    void baseResumeContent_readsRequestedResourceAndReturnsText() {
        UUID resumeId = UUID.randomUUID();
        BaseResumeContentResponse response = new BaseResumeContentResponse(resumeId, "Base CV", "EN", true, false, "base-text");
        when(resumeGenerationService.getBaseResumeContent(resumeId)).thenReturn(response);

        String result = baseResumeContentResource.baseResumeContent(resumeId.toString());

        assertThat(result).isEqualTo("base-text");
    }

    @Test
    void generatedResumeContent_readsRequestedResourceAndReturnsText() {
        UUID applicationId = UUID.randomUUID();
        GeneratedResumeContentResponse response = new GeneratedResumeContentResponse(applicationId, "file-1", "resume.docx", "generated-text");
        when(resumeGenerationService.getGeneratedResumeContent(applicationId)).thenReturn(response);

        String result = generatedResumeContentResource.generatedResumeContent(applicationId.toString());

        assertThat(result).isEqualTo("generated-text");
    }
}
