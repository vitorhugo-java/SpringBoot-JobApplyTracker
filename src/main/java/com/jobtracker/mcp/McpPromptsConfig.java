package com.jobtracker.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Registers MCP prompt templates. The Spring AI MCP server auto-configuration collects beans of
 * type List&lt;McpServerFeatures.SyncPromptSpecification&gt; and exposes them via the
 * prompts/list and prompts/get protocol endpoints.
 *
 * Prompts are parameterised instructions that tell an MCP client (e.g. Claude) exactly which
 * tools to call and in which order to accomplish a guided workflow.
 */
@Configuration
public class McpPromptsConfig {

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> mcpPrompts() {
        return List.of(
                prepareNewApplicationPrompt(),
                tailorResumePrompt(),
                summarizePipelinePrompt()
        );
    }

    private McpServerFeatures.SyncPromptSpecification prepareNewApplicationPrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "prepare_new_application",
                "Guides the user through logging a new job application step-by-step",
                List.of(
                        new McpSchema.PromptArgument("vacancyName",
                                "The job title or vacancy name, e.g. 'Backend Engineer'", false),
                        new McpSchema.PromptArgument("recruiterName",
                                "Recruiter's full name if known", false),
                        new McpSchema.PromptArgument("organization",
                                "Company or organisation name", false)
                )
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String vacancy   = getArg(req, "vacancyName",   "(not yet informed)");
            String recruiter = getArg(req, "recruiterName", "(not yet informed)");
            String org       = getArg(req, "organization",  "(not yet informed)");

            String text = """
                    You are helping me log a new job application in my tracker.

                    Known details so far:
                    - Vacancy: %s
                    - Recruiter: %s
                    - Organisation: %s

                    Please ask me for any missing required fields (applicationDate, rhAcceptedConnection,
                    interviewScheduled, recruiterDmReminderEnabled) and then call the `createApplication`
                    tool with the complete data. Use status "RH" for a standard LinkedIn/HR cold outreach.
                    """.formatted(vacancy, recruiter, org);

            return new McpSchema.GetPromptResult(
                    "Prepare new application: " + vacancy,
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private McpServerFeatures.SyncPromptSpecification tailorResumePrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "tailor_resume",
                "Generates a tailored resume for a specific application using Google Drive",
                List.of(
                        new McpSchema.PromptArgument("applicationId",
                                "UUID of the target job application", true)
                )
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String appId = getArg(req, "applicationId", "");

            String text = """
                    I want to tailor a resume for job application ID: %s

                    Please follow these steps:
                    1. Call `getApplication` with id="%s" to see the vacancy name and organisation.
                    2. Call `listBaseResumes` to see available resume templates.
                    3. Ask me which base resume template to use if more than one exists.
                    4. Call `copyResumeToApplication` with the applicationId and the chosen baseResumeId.
                    5. Return the Google Docs link from the response so I can start editing.
                    """.formatted(appId, appId);

            return new McpSchema.GetPromptResult(
                    "Tailor resume for application " + appId,
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private McpServerFeatures.SyncPromptSpecification summarizePipelinePrompt() {
        McpSchema.Prompt prompt = new McpSchema.Prompt(
                "summarize_pipeline",
                "Produces a human-readable summary of the current job search pipeline",
                List.of()
        );

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, req) -> {
            String text = """
                    Please summarise my current job search pipeline by following these steps:

                    1. Call `getPipelineSummary` to get aggregate statistics.
                    2. Call `listApplications` with no filters (page=0, size=10, sort=createdAt,desc)
                       to retrieve the 10 most recent applications.
                    3. Call `getOverdueApplications` to identify follow-ups that need immediate action.
                    4. Call `getGamificationProfile` to include my level and XP in the summary.

                    Produce a concise report that includes:
                    - Total applications and status breakdown
                    - Interview count vs total
                    - Overdue follow-ups requiring action
                    - Daily/weekly application rate
                    - Current gamification level, XP, and streak
                    """;

            return new McpSchema.GetPromptResult(
                    "Pipeline summary",
                    List.of(new McpSchema.PromptMessage(
                            McpSchema.Role.USER,
                            new McpSchema.TextContent(text))));
        });
    }

    private static String getArg(McpSchema.GetPromptRequest req, String key, String defaultValue) {
        if (req == null || req.arguments() == null) {
            return defaultValue;
        }
        Map<String, Object> args = req.arguments();
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        String strValue = value.toString();
        return strValue.isBlank() ? defaultValue : strValue;
    }
}
