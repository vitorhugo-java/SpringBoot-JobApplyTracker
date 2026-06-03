package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gdrive.BaseResumeResponse;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyRequest;
import com.jobtracker.dto.gdrive.GoogleDriveResumeCopyResponse;
import com.jobtracker.dto.gdrive.GoogleDriveStatusResponse;
import com.jobtracker.service.GoogleDriveService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.util.List;
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

    public McpGoogleDriveTools(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    @Tool(description = """
            Get current Google Drive integration status for the authenticated user:
            connected account, root folder, and whether base resumes have been configured.
            Requires BETA role.
            """)
    public GoogleDriveStatusResponse getDriveStatus() {
        return googleDriveService.getStatus();
    }

    @Tool(description = """
            List all base resume templates configured in Google Drive for the authenticated user.
            Requires BETA role.
            """)
    public List<BaseResumeResponse> listBaseResumes() {
        return googleDriveService.listBaseResumes();
    }

    @Tool(description = """
            Copy a base resume template into the application's Google Drive folder to generate
            a tailored resume. Returns a link to the newly created Google Doc.
            Requires BETA role.
            """)
    public GoogleDriveResumeCopyResponse copyResumeToApplication(
            @ToolParam(description = "UUID of the job application") String applicationId,
            @ToolParam(description = "UUID of the base resume template to copy") String baseResumeId) {
        return googleDriveService.copyBaseResumeToApplication(
                UUID.fromString(applicationId),
                new GoogleDriveResumeCopyRequest(UUID.fromString(baseResumeId)));
    }
}
