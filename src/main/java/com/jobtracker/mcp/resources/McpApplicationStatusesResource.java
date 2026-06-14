package com.jobtracker.mcp.resources;

import com.jobtracker.entity.ApplicationStatusEntity;
import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.repository.ApplicationStatusRepository;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.stereotype.Service;

@Service
public class McpApplicationStatusesResource {

    private static final String LAST_MODIFIED = "2026-06-08";

    private final ApplicationStatusRepository applicationStatusRepository;

    public McpApplicationStatusesResource(ApplicationStatusRepository applicationStatusRepository) {
        this.applicationStatusRepository = applicationStatusRepository;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_APPLICATION_STATUSES,
            name = "Application Statuses",
            title = "Application Statuses",
            description = "Markdown catalog of valid application statuses.",
            mimeType = "text/markdown",
            annotations = @McpAnnotations(
                    audience = {Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.9d))
    @AuditMcpOperation(action = "Application Statuses")
    public String applicationStatuses(McpSyncServerExchange exchange) {
        StringBuilder text = new StringBuilder("""
                # Valid Application Status Values

                Use the exact names below (case-sensitive). Call List-Statuses or \
                GET /api/v1/applications/statuses to retrieve them at runtime.

                """);

        for (ApplicationStatusEntity status : applicationStatusRepository.findAllByOrderByDisplayOrderAsc()) {
            text.append("- ").append(status.getName()).append('\n');
        }

        text.append("\nOmit status (pass null) when logging a draft/to-send-later application.\n");
        return text.toString();
    }
}
