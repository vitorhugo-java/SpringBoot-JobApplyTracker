package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.service.ResumeGenerationService.GeneratedResumeContentResponse;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

@PreAuthorize("hasRole('BETA')")
@Service
public class McpGeneratedResumeContentResource {

    private final ResumeGenerationService resumeGenerationService;

    public McpGeneratedResumeContentResource(ResumeGenerationService resumeGenerationService) {
        this.resumeGenerationService = resumeGenerationService;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_GENERATED_RESUME_CONTENT,
            name = "Generated Resume Content",
            mimeType = "text/plain")
    public String generatedResumeContent(String applicationId) {
        GeneratedResumeContentResponse response = resumeGenerationService.getGeneratedResumeContent(UUID.fromString(applicationId));
        return response.content();
    }
}
