package com.jobtracker.mcp.resources;

import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.service.ResumeGenerationService;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

@PreAuthorize("hasRole('BETA')")
@Service
public class McpBaseResumeContentResource {

    private final ResumeGenerationService resumeGenerationService;

    public McpBaseResumeContentResource(ResumeGenerationService resumeGenerationService) {
        this.resumeGenerationService = resumeGenerationService;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_BASE_RESUME_CONTENT,
            name = "Base Resume Content",
            mimeType = "text/plain")
    public String baseResumeContent(String resumeId) {
        BaseResumeContentResponse response = resumeGenerationService.getBaseResumeContent(UUID.fromString(resumeId));
        return response.content();
    }
}
