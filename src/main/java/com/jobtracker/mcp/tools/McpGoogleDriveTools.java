package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gdrive.BaseResumeContentResponse;
import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.GoogleDriveStatusResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderDetectionResponse;
import com.jobtracker.dto.gdrive.ResumePlaceholderRequest;
import com.jobtracker.dto.gdrive.ResumePlaceholderResponse;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService.DownloadedFile;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import com.jobtracker.service.ResumeGenerationService.GeneratedResumeContentResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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

    @Tool(description = """
            Get current Google Drive integration status for the authenticated user:
            connected account, root folder, and whether base resumes have been configured.
            Requires BETA role.
            """, name = "Google Drive Status")
    public GoogleDriveStatusResponse getDriveStatus() {
        return googleDriveService.getStatus();
    }

    @Tool(description = """
            List all base resume templates configured in Google Drive for the authenticated user.
            Requires BETA role.
            """, name = "List Base Resumes")
    public List<BaseResumeResponse> listBaseResumes() {
        return googleDriveService.listBaseResumes();
    }

    @Tool(description = """
            Copy a base resume template into the application's Google Drive folder to generate
            a tailored resume. Returns a link to the newly created Google Doc.
            Requires BETA role.
            """, name = "Copy Base Resume to Application")
    public GoogleDriveResumeCopyResponse copyResumeToApplication(
            @ToolParam(description = "UUID of the job application") String applicationId,
            @ToolParam(description = "UUID of the base resume template to copy") String baseResumeId) {
        return googleDriveService.copyBaseResumeToApplication(
                UUID.fromString(applicationId),
                new GoogleDriveResumeCopyRequest(UUID.fromString(baseResumeId)));
    }

    @Tool(description = """
            Read the plain text content of a base resume template from Google Docs.
            Template placeholders such as {{RESUMO}} and {{SKILLS}} are preserved as-is so they
            can be analyzed before generating a tailored resume.
            The resumeId MUST be the base resume UUID — filenames and Google file IDs are NOT valid.
            Requires BETA role.
            """, name = "Get Base Resume Content")
    public BaseResumeContentResponse getBaseResumeContent(@ToolParam(description = "UUID of the base resume (not the filename)") String resumeId) {
        return resumeGenerationService.getBaseResumeContent(UUID.fromString(resumeId));
    }

    @Tool(description = """
            Read the plain text content of the resume already generated for a job application.
            Returns 400/error if no resume has been generated for the application yet.
            Requires BETA role.
            """, name = "Get Generated Resume Content")
    public GeneratedResumeContentResponse getGeneratedResumeContent(@ToolParam(description = "UUID of the job application") String applicationId) {
        return resumeGenerationService.getGeneratedResumeContent(UUID.fromString(applicationId));
    }

    @Tool(description = """
            Detect the template placeholders present in a base resume so values can be supplied
            before generating a tailored resume. Returns the placeholder names without curly braces
            (e.g. "RESUMO", "SKILLS").
            The baseResumeId MUST be the base resume UUID — filenames are NOT valid.
            Requires BETA role.
            """, name = "Detect Resume Placeholders")
    public ResumePlaceholderDetectionResponse detectResumePlaceholders(@ToolParam(description = "UUID of the base resume (not the filename)") String baseResumeId) {
        return resumeGenerationService.detectPlaceholders(UUID.fromString(baseResumeId));
    }

    @Tool(description = """
            Generate a tailored resume for a job application by copying a base resume template and
            replacing its placeholders with the supplied values. A Google Doc and a PDF are created
            in the application's Drive folder.
            values is REQUIRED: a map keyed by placeholder name WITHOUT curly braces — e.g.
            {"RESUMO": "Senior Java Developer", "HARDSKILL1": "Java"}. Keys must match the names
            returned by detectResumePlaceholders. Provide a value for every detected placeholder.
            Requires BETA role.
            """, name = "Generate Resume from Template")
    public ResumePlaceholderResponse generateResume(
            @ToolParam(description = "UUID of the job application") String applicationId,
            @ToolParam(description = "UUID of the base resume template to use") String baseResumeId,
            @ToolParam(description = "Placeholder values keyed by name without braces, e.g. {\"RESUMO\":\"...\"}")
            Map<String, String> values) {
        return resumeGenerationService.generateTemplateResume(
                UUID.fromString(applicationId),
                new ResumePlaceholderRequest(UUID.fromString(baseResumeId), values));
    }

    @Tool(description = """
            Download the generated resume of a job application as a PDF. The binary file is returned
            Base64-encoded together with its filename and content type. Generate a resume first
            (generateResume) before calling this.
            Requires BETA role.
            """, name = "Download Generated Resume PDF")
    public GeneratedResumePdf downloadGeneratedResumePdf(
            @ToolParam(description = "UUID of the job application") String applicationId) {
        DownloadedFile file = generatedResumeDownloadService.downloadAsPdf(UUID.fromString(applicationId));
        return new GeneratedResumePdf(
                file.fileName(),
                file.contentType(),
                Base64.getEncoder().encodeToString(file.content()));
    }

    /** Base64-encoded PDF payload returned by {@link #downloadGeneratedResumePdf(String)}. */
    public record GeneratedResumePdf(String fileName, String contentType, String base64Content) {}
}
