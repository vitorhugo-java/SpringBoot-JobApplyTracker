package com.jobtracker.mcp.resources;

import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.stereotype.Service;

@Service
public class McpResumeWorkflowRulesResource {

    private static final String LAST_MODIFIED = "2026-06-13";

    @McpResource(
            uri = McpResourcesConfig.URI_RESUME_WORKFLOW_RULES,
            name = "Resume Workflow Rules",
            title = "Resume Workflow Rules",
            description = "Markdown instructions for the resume tailoring workflow.",
            mimeType = "text/markdown",
            annotations = @McpAnnotations(
                    audience = {Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 1.0d))
    @AuditMcpOperation(action = "Resume Workflow Rules")
    public String resumeWorkflowRules(McpSyncServerExchange exchange) {
        return """
                # Resume Workflow Rules

                Execute these steps in order:

                1. Read my base information FIRST (mandatory, top priority). Call List-Base-Information and read every
                   document via Get-Base-Information-Content (or resource://job-apply-tracker/base-information/{infoId}).
                   This is the AUTHORITATIVE, highest-priority source of truth about the candidate. You MUST read it
                   before generating any CV content. If no base information exists, fall back to step 2.
                2. (Secondary) List-Applications — find the newest application with a non-empty driveResumeFileId and read
                   resource://job-apply-tracker/generated-resume/{applicationId} as a supplementary CV source.
                3. Call List-Base-Resumes — pick the template by vacancy language (PT → PT-BR template, EN → EN-US template).
                   Alternatively read resource://job-apply-tracker/base-resumes if the tool is unavailable.
                4. Detect-Resume-Placeholders — call this before Generate-Resume every time.
                5. Ask only for missing information that is required to fill placeholders or log the application.
                6. Create-Application — save the application before generating the tailored resume.
                7. Generate-Resume — provide every detected placeholder value.

                Rules:
                - ALWAYS read base information (step 1) before generating any CV content when any base information exists.
                  Base information is the authoritative source; a prior generated resume is only a secondary supplement.
                - Never use a base resume template as the data source.
                - Never invent experience, technologies, projects, or certifications — ground everything in base information.
                - Placeholder keys must match Detect-Resume-Placeholders exactly, without curly braces.
                - If neither base information nor a prior generated resume exists, ask the user for their CV text because
                  this MCP setup does not expose generic Drive search or file-read tools.
                - If Generate-Resume fails, keep the application record and return the placeholder values.

                ID disambiguation:
                - baseResumeId: a Job Apply Tracker UUID returned by List-Base-Resumes or
                  resource://job-apply-tracker/base-resumes. This is NOT a Google Drive fileId.
                - Google Drive fileId: a Drive string (e.g. "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgVE2upms").
                  These two IDs are not interchangeable — always use the Job Apply Tracker UUID.
                """;
    }
}
