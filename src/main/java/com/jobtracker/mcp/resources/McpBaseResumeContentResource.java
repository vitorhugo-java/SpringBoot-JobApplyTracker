package com.jobtracker.mcp.resources;

import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.ResumeGenerationService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

@PreAuthorize("hasRole('BETA')")
@Service
public class McpBaseResumeContentResource {

    private static final String LAST_MODIFIED = "2026-06-04";

    private final ResumeGenerationService resumeGenerationService;

    public McpBaseResumeContentResource(ResumeGenerationService resumeGenerationService) {
        this.resumeGenerationService = resumeGenerationService;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_BASE_RESUME_CONTENT,
            name = "Base Resume Content",
            title = "Base Resume Content",
            description = "Plain-text content of a stored base resume template.",
            mimeType = "text/plain",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.8d))
    @AuditMcpOperation(action = "Base Resume Content")
    public String baseResumeContent(McpSyncServerExchange exchange, String resumeId) {
        BaseResumeContentResponse response = resumeGenerationService.getBaseResumeContent(UUID.fromString(resumeId));
        return response.content();
    }
}
