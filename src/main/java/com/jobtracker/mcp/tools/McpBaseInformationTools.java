package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gdrive.BaseInformationContentResponse;
import com.jobtracker.dto.gdrive.BaseInformationResponse;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.BaseInformationService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * MCP tools exposing the candidate's "base information about me" documents.
 * Mirrors the @PreAuthorize("hasRole('BETA')") guard that GoogleDriveController applies at the REST layer.
 */
@PreAuthorize("hasRole('BETA')")
@Component
public class McpBaseInformationTools {

    private final BaseInformationService baseInformationService;

    public McpBaseInformationTools(BaseInformationService baseInformationService) {
        this.baseInformationService = baseInformationService;
    }

    @McpTool(
            name = "List-Base-Information",
            title = "List Base Information",
            description = """
                    List the candidate's "base information about me" documents (Google Docs, PDF, DOCX, or Markdown) \
                    hosted on Google Drive.

                    This is the AUTHORITATIVE, highest-priority source of truth about the candidate — their real \
                    experience, projects, skills, and background. ALWAYS call this (and read each document with \
                    Get-Base-Information-Content) BEFORE generating any CV content, and prefer it over prior resumes.

                    Each entry contains:
                    - id (UUID): pass this to Get-Base-Information-Content.
                    - name: display name of the document.
                    - docType: GOOGLE_DOC, PDF, DOCX, or MARKDOWN.
                    - createdAt: registration timestamp.""",
            annotations = @McpAnnotations(
                    title = "List Base Information",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    @AuditMcpOperation(action = "List-Base-Information")
    public List<BaseInformationResponse> listBaseInformation(McpSyncRequestContext ctx) {
        return baseInformationService.listBaseInformation();
    }

    @McpTool(
            name = "Get-Base-Information-Content",
            title = "Get Base Information Content",
            description = """
                    Return the full plain-text content of a base information document (the authoritative source of \
                    truth about the candidate). Call List-Base-Information first to obtain a valid id, then read \
                    every document before generating any CV content. Never invent experience, technologies, projects, \
                    or certifications — ground everything in this content.""",
            annotations = @McpAnnotations(
                    title = "Get Base Information Content",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Get-Base-Information-Content")
    public BaseInformationContentResponse getBaseInformationContent(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the base information document — obtain from List-Base-Information") String id) {
        return baseInformationService.getBaseInformationContent(UUID.fromString(id));
    }
}
