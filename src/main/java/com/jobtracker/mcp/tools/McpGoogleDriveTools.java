package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService.DownloadedFile;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for Google Drive resume automation.
 * Mirrors the @PreAuthorize("hasRole('BETA')") guard that GoogleDriveController applies at the
 * REST layer. Because these tools call GoogleDriveService directly (bypassing the controller),
 * the class-level @PreAuthorize replicates the same restriction so non-BETA users receive 403.
 */
@PreAuthorize("hasRole('BETA')")
@Component
public class McpGoogleDriveTools {

    private final GoogleDriveService googleDriveService;
    private final ResumeGenerationService resumeGenerationService;
    private final GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService;

    public McpGoogleDriveTools(GoogleDriveService googleDriveService,
                               ResumeGenerationService resumeGenerationService,
                               GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService) {
        this.googleDriveService = googleDriveService;
        this.resumeGenerationService = resumeGenerationService;
        this.generatedResumeDownloadService = generatedResumeDownloadService;
    }

    @McpTool(
            name = "List-Base-Resumes",
            title = "List Base Resumes",
            description = """
                    List all base resume templates configured by the current user.

                    Each entry contains:
                    - id (UUID): the baseResumeId required by Copy-Resume-To-Application, Generate-Resume, \
                    and Detect-Resume-Placeholders. Never use a Google Drive fileId here.
                    - name: display name of the document (e.g. "BASE - CV - Vitor Hugo PT-BR").
                    - language: language code of the resume (e.g. "PT", "EN"). Use to select the correct \
                    template for the vacancy language (PT → PT-BR template, EN → EN-US template).
                    - template: true if this is a reusable placeholder template.
                    - readOnly: true if this is a read-only PDF resume (cannot be used for template \
                    generation, placeholder detection, or copying to applications — only content \
                    reading is supported).
                    - createdAt: registration timestamp.

                    Call this tool before any resume operation to obtain a valid baseResumeId.""",
            annotations = @McpAnnotations(
                    title = "List Base Resumes",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    @AuditMcpOperation(action = "List-Base-Resumes")
    public List<BaseResumeResponse> listBaseResumes(McpSyncRequestContext ctx) {
        return googleDriveService.listBaseResumes();
    }

    @McpTool(
            name = "Copy-Resume-To-Application",
            title = "Copy Resume To Application",
            description = """
                    Copy a base resume template into the application's Google Drive folder and return \
                    the new Google Doc link and folder details.

                    Use List-Base-Resumes to obtain a valid baseResumeId before calling this tool. \
                    The baseResumeId is a Job Apply Tracker UUID — it is NOT a Google Drive file ID. \
                    The application folder is created automatically inside the configured Drive root folder \
                    if it does not yet exist.""",
            annotations = @McpAnnotations(
                    title = "Copy Resume To Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Copy-Resume-To-Application")
    public GoogleDriveResumeCopyResponse copyResumeToApplication(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the job application (from Create-Application or List-Applications)") String applicationId,
            @McpToolParam(required = true, description = "UUID of the base resume template — obtain from List-Base-Resumes, NOT a Google Drive file ID") String baseResumeId) {
        return googleDriveService.copyBaseResumeToApplication(
                UUID.fromString(applicationId),
                new GoogleDriveResumeCopyRequest(UUID.fromString(baseResumeId)));
    }

    @McpTool(
            name = "Detect-Resume-Placeholders",
            title = "Detect Resume Placeholders",
            description = """
                    Scan a base resume template and return the list of placeholder names found inside \
                    double curly braces (e.g. {{RESUMO}}, {{STACK}}).

                    Always call this before Generate-Resume so you know which keys to supply. \
                    Use List-Base-Resumes to obtain the baseResumeId. \
                    The returned placeholder names must be used without braces as keys in Generate-Resume.""",
            annotations = @McpAnnotations(
                    title = "Detect Resume Placeholders",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Detect-Resume-Placeholders")
    public ResumePlaceholderDetectionResponse detectResumePlaceholders(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the base resume template — obtain from List-Base-Resumes") String baseResumeId) {
        return resumeGenerationService.detectPlaceholders(UUID.fromString(baseResumeId));
    }

    @McpTool(
            name = "Generate-Resume",
            title = "Generate Resume",
            description = """
                    Copy a base resume template into the application's Drive folder, replace all \
                    placeholders with the supplied values, export a PDF, and return links to both \
                    the Google Doc and the PDF.

                    Prerequisites (call in order before this tool):
                    1. List-Base-Information + Get-Base-Information-Content — you MUST read the candidate's base \
                       information first. It is the authoritative source of truth about the candidate; never generate \
                       values from the template/vacancy alone and never invent experience, skills, or projects.
                    2. List-Base-Resumes — obtain the baseResumeId for the vacancy language.
                    3. Detect-Resume-Placeholders — retrieve the exact placeholder key names.
                    4. Provide a value for every detected placeholder key (keys without braces, \
                       e.g. "RESUMO", "STACK", "PROJETO_1").

                    Do NOT call this tool before Create-Application — you need the applicationId first.""",
            annotations = @McpAnnotations(
                    title = "Generate Resume",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Generate-Resume")
    public ResumePlaceholderResponse generateResume(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the job application — must already exist (from Create-Application)") String applicationId,
            @McpToolParam(required = true, description = "UUID of the base resume template — obtain from List-Base-Resumes, NOT a Google Drive file ID") String baseResumeId,
            @McpToolParam(required = true, description = "Placeholder values keyed by the exact names returned by Detect-Resume-Placeholders (without braces), e.g. {\"RESUMO\":\"...\", \"STACK\":\"Java, Spring Boot\"}")
            Map<String, String> values) {
        return resumeGenerationService.generateTemplateResume(
                UUID.fromString(applicationId),
                new ResumePlaceholderRequest(UUID.fromString(baseResumeId), values));
    }

    @McpTool(
            name = "Download-Generated-Resume-PDF",
            title = "Download Generated Resume PDF",
            description = "Download the generated resume PDF for an application as Base64-encoded content.",
            annotations = @McpAnnotations(
                    title = "Download Generated Resume PDF",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    @AuditMcpOperation(action = "Download-Generated-Resume-PDF")
    public GeneratedResumePdf downloadGeneratedResumePdf(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "UUID of the job application") String applicationId) {
        DownloadedFile file = generatedResumeDownloadService.downloadAsPdf(UUID.fromString(applicationId));
        return new GeneratedResumePdf(
                file.fileName(),
                file.contentType(),
                Base64.getEncoder().encodeToString(file.content()));
    }

    /** Base64-encoded PDF payload returned by {@link #downloadGeneratedResumePdf(String)}. */
    public record GeneratedResumePdf(String fileName, String contentType, String base64Content) {}
}
