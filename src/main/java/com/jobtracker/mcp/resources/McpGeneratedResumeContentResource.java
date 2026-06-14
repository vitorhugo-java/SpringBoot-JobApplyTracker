package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.service.ResumeGenerationService.GeneratedResumeContentResponse;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

@PreAuthorize("hasRole('BETA')")
@Service
public class McpGeneratedResumeContentResource {

    private static final String LAST_MODIFIED = "2026-06-04";

    private final ResumeGenerationService resumeGenerationService;

    public McpGeneratedResumeContentResource(ResumeGenerationService resumeGenerationService) {
        this.resumeGenerationService = resumeGenerationService;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_GENERATED_RESUME_CONTENT,
            name = "Generated Resume Content",
            title = "Generated Resume Content",
            description = "Plain-text content of the generated resume for an application.",
            mimeType = "text/plain",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.9d))
    @AuditMcpOperation(action = "Generated Resume Content")
    public String generatedResumeContent(McpSyncServerExchange exchange, String applicationId) {
        GeneratedResumeContentResponse response = resumeGenerationService.getGeneratedResumeContent(UUID.fromString(applicationId));
        return response.content();
    }
}
