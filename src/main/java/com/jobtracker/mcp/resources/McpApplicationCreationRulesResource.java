package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.stereotype.Service;

@Service
public class McpApplicationCreationRulesResource {

    private static final String LAST_MODIFIED = "2026-06-04";

    @McpResource(
            uri = McpResourcesConfig.URI_APPLICATION_CREATION_RULES,
            name = "Application Creation Rules",
            title = "Application Creation Rules",
            description = "Markdown defaults and invariants for creating or updating applications.",
            mimeType = "text/markdown",
            annotations = @McpAnnotations(
                    audience = {Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 1.0d))
    @AuditMcpOperation(action = "Application Creation Rules")
    public String applicationCreationRules(McpSyncServerExchange exchange) {
        return """
                # Application Creation Rules

                Apply these defaults on every Create-Application or Update-Application call:

                - applicationDate: always today's date in yyyy-MM-dd format. Never the vacancy posting date.
                - nextStepDateTime: do not auto-fill. Set only when the user explicitly provides it.
                - status: omit (null) for a fresh cold outreach. Use "RH" only when already in process.
                - recruiterDmReminderEnabled: true only when a recruiter email or contact exists.
                - rhAcceptedConnection: false unless the LinkedIn connection is confirmed accepted.
                - interviewScheduled: false unless an interview is confirmed.
                """;
    }
}
