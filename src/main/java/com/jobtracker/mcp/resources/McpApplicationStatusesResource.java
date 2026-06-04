package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.entity.enums.ApplicationStatus;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

@Service
public class McpApplicationStatusesResource {

    @McpResource(
            uri = McpResourcesConfig.URI_APPLICATION_STATUSES,
            name = "Application Statuses",
            mimeType = "text/plain")
    public String applicationStatuses() {
        StringBuilder text = new StringBuilder("""
                # Valid Application Status Values

                Use the exact display names below (case-sensitive):

                """);

        for (ApplicationStatus status : ApplicationStatus.values()) {
            text.append("- ")
                    .append(status.getDisplayName())
                    .append(" — ")
                    .append(status.getDescription())
                    .append('\n');
        }

        text.append("\nOmit status (pass null) when logging a fresh cold outreach application.\n");
        return text.toString();
    }
}
