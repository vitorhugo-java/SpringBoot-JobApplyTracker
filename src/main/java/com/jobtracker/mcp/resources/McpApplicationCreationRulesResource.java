package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;

@Service
public class McpApplicationCreationRulesResource {

    @McpResource(
            uri = McpResourcesConfig.URI_APPLICATION_CREATION_RULES,
            name = "Application Creation Rules",
            mimeType = "text/plain")
    public String applicationCreationRules() {
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
