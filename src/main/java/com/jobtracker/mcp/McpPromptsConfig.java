package com.jobtracker.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Registers MCP prompt templates.
 */
@Service
public class McpPromptsConfig {

    /**
     * Full autonomous vacancy-intake workflow.
     */
    @McpPrompt(name = "Intake-Vacancy", description = "Execute the autonomous application intake workflow from a pasted vacancy")
    public GetPromptResult intakeVacancyPrompt(
            @McpArg(name = "vacancyContent", description = "Job description, link, recruiter message, or LinkedIn post", required = true)
            String vacancyContent) {
        String text = """
                You are my software engineering application assistant. Always communicate with me in PT-BR.
                Content generated for the CV must be written in the vacancy language.

                Execute the complete workflow autonomously, without waiting for intermediate confirmations.
                Stop only for genuinely necessary questions (see Step 5).

                Before executing steps 7 and 8, read the resources:
                  %s  (application field rules)
                  %s  (mandatory CV generation sequence)
                  %s  (allowed status values)

                === VACANCY ===
                %s
                === END VACANCY ===

                Follow this order exactly:

                STEP 1 - Analyze the vacancy
                Extract: vacancyName (title), organization (company), vacancyLink (URL if present),
                required stack, seniority, vacancy language, recruiter name/email if present.

                STEP 2 - Read my REAL resume (required before generating any content)
                - Call List-Applications and select the most recent application with driveResumeFileId populated.
                - If such an application exists, read resource://job-apply-tracker/generated-resume/{applicationId}
                  for the selected application and use that text as the real CV source.
                - If no prior CV exists, ask for the current CV text because this MCP does not expose generic
                  Drive search or file reading.
                Extract: experience, real stack, projects, education, certifications, achievements, languages.
                NEVER invent experience, technologies, projects, or certifications.

                STEP 3 - List and select a CV template
                Read resource://job-apply-tracker/base-resumes. Select by language (PT→PT-BR; EN→EN-US).
                Do not ask if there is only one per language.

                STEP 4 - Detect placeholders
                Call Detect-Resume-Placeholders with the selected baseResumeId (see resume-workflow-rules).

                STEP 5 - Minimum questions (only if needed)
                Ask ONLY if a piece of information is missing from both the real CV AND the vacancy, and is required
                for a placeholder or the record. Never ask about technologies or background.

                STEP 6 - Generate placeholder values
                Cross-check the real CV (step 2) with the vacancy requirements (step 1). ATS-friendly, without inventing anything.
                Follow resume-workflow-rules for completeness and key formatting.

                STEP 7 - Create the application
                Follow application-creation-rules. Call Create-Application with the extracted data.
                Do NOT fill nextStepDateTime.
                Note: ATS-focused summary (stack, seniority, fit, gaps).

                STEP 8 - Generate the filled CV
                Only after Create-Application returns a valid UUID AND all placeholders are generated.
                Follow resume-workflow-rules. Call Generate-Resume and return the Google Doc link.

                STEP 9 - Final delivery (in PT-BR)
                1. Link to the generated CV
                2. Detected placeholders + generated value for each one
                3. UUID and status of the created application
                """.formatted(
                McpResourcesConfig.URI_APPLICATION_CREATION_RULES,
                McpResourcesConfig.URI_RESUME_WORKFLOW_RULES,
                McpResourcesConfig.URI_APPLICATION_STATUSES,
                vacancyContent);

        return new GetPromptResult(
                "Intake Vacancy",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(name = "Prepare-New-Application", description = "Guide the user through logging a new job application step-by-step")
    public GetPromptResult prepareNewApplicationPrompt(
            @McpArg(name = "vacancyName", description = "The job title or vacancy name, e.g. 'Backend Engineer'", required = false)
            String vacancyName,
            @McpArg(name = "recruiterName", description = "Recruiter's full name if known", required = false)
            String recruiterName,
            @McpArg(name = "organization", description = "Company or organization name", required = false)
            String organization) {
        String text = """
                You are helping me log a new job application in my tracker.

                Known details so far:
                - Vacancy: %s
                - Recruiter: %s
                - Organization: %s

                Read %s for field defaults before calling Create-Application. Ask me for any missing
                required fields (rhAcceptedConnection, interviewScheduled, recruiterDmReminderEnabled),
                then call Create-Application with the complete data. Use status "RH" for a standard
                LinkedIn/HR cold outreach.
                """.formatted(
                valueOrDefault(vacancyName),
                valueOrDefault(recruiterName),
                valueOrDefault(organization),
                McpResourcesConfig.URI_APPLICATION_CREATION_RULES);

        return new GetPromptResult(
                "Prepare-New-Application: " + valueOrDefault(vacancyName),
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(name = "Tailor-Resume", description = "Generate a tailored resume for a specific application using Google Drive")
    public GetPromptResult tailorResumePrompt(
            @McpArg(name = "applicationId", description = "UUID of the target job application", required = true)
            String applicationId) {
        String text = """
                I want to tailor a resume for job application ID: %s

                1. Call `Get-Application` with id="%s" to see the vacancy name and organization.
                2. Read `resource://job-apply-tracker/base-resumes` to see available resume templates.
                3. Ask me which base resume template to use if more than one exists.
                4. Call `Copy-Resume-To-Application` with the applicationId and the chosen baseResumeId.
                5. Return the Google Docs link from the response so I can start editing.
                """.formatted(applicationId, applicationId);

        return new GetPromptResult(
                "Tailor-Resume for Application " + applicationId,
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(name = "Summarize-Pipeline", description = "Produce a human-readable summary of the current job search pipeline")
    public GetPromptResult summarizePipelinePrompt() {
        String text = """
                Please summarize my current job search pipeline:

                1. Read `resource://job-apply-tracker/pipeline-summary` for aggregate statistics.
                2. Call `List-Applications` (page=0, size=10, sort=createdAt,desc) for recent applications.
                3. Call `Get-Overdue-Applications` to identify follow-ups needing immediate action.
                4. Read `resource://job-apply-tracker/gamification-profile` to include level and XP.

                Report: total applications, status breakdown, interview count, overdue follow-ups,
                daily/weekly rate, gamification level, XP, and streak.
                """;

        return new GetPromptResult(
                "Summarize-Pipeline",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    private static String valueOrDefault(String value) {
        return value == null || value.isBlank() ? "(not yet informed)" : value;
    }
}
