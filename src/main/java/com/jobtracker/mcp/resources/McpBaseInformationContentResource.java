package com.jobtracker.mcp.resources;

import com.jobtracker.dto.gdrive.BaseInformationContentResponse;
import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.BaseInformationService;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.UUID;

@PreAuthorize("hasRole('BETA')")
@Service
public class McpBaseInformationContentResource {

    private static final String LAST_MODIFIED = "2026-06-13";

    private final BaseInformationService baseInformationService;

    public McpBaseInformationContentResource(BaseInformationService baseInformationService) {
        this.baseInformationService = baseInformationService;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_BASE_INFORMATION_CONTENT,
            name = "Base Information Content",
            title = "Base Information Content",
            description = "Plain-text content of a base information document — the authoritative source of truth about the candidate.",
            mimeType = "text/plain",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.95d))
    @AuditMcpOperation(action = "Base Information Content")
    public String baseInformationContent(McpSyncServerExchange exchange, String infoId) {
        BaseInformationContentResponse response = baseInformationService.getBaseInformationContent(UUID.fromString(infoId));
        return response.content();
    }
}
