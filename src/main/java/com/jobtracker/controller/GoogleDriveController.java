package com.jobtracker.controller;

import com.jobtracker.dto.auth.MessageResponse;
import com.jobtracker.dto.gdrive.*;
import com.jobtracker.service.GoogleDriveGeneratedResumeDownloadService;
import com.jobtracker.service.GoogleDriveOAuthService;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.service.ResumeGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Tag(name = "Google Drive", description = "Google Drive OAuth and resume copy endpoints")
@RestController
@RequestMapping("/api/v1/google-drive")
public class GoogleDriveController {

    private final GoogleDriveOAuthService googleDriveOAuthService;
    private final GoogleDriveService googleDriveService;
    private final ResumeGenerationService resumeGenerationService;
    private final GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService;

    public GoogleDriveController(GoogleDriveOAuthService googleDriveOAuthService,
                                 GoogleDriveService googleDriveService,
                                 ResumeGenerationService resumeGenerationService,
                                 GoogleDriveGeneratedResumeDownloadService generatedResumeDownloadService) {
        this.googleDriveOAuthService = googleDriveOAuthService;
        this.googleDriveService = googleDriveService;
        this.resumeGenerationService = resumeGenerationService;
        this.generatedResumeDownloadService = generatedResumeDownloadService;
    }

    @Operation(
            summary = "Start Google Drive OAuth flow",
            responses = @ApiResponse(responseCode = "200", description = "Authorization URL generated",
                    content = @Content(schema = @Schema(implementation = GoogleDriveOAuthStartResponse.class)))
    )
    @PreAuthorize("hasRole('BETA')")
    @PostMapping("/oauth/start")
    public ResponseEntity<GoogleDriveOAuthStartResponse> startOauth() {
        return ResponseEntity.ok(googleDriveOAuthService.startAuthorization());
    }

    @Operation(summary = "Google Drive OAuth callback")
    @GetMapping("/oauth/callback")
    public void oauthCallback(@RequestParam(required = false) String state,
                              @RequestParam(required = false) String code,
                              @RequestParam(required = false) String error,
                              HttpServletResponse response) throws IOException {
        response.sendRedirect(googleDriveOAuthService.handleCallback(state, code, error));
    }

    @Operation(
            summary = "Get Google Drive status",
            responses = @ApiResponse(responseCode = "200", description = "Current Google Drive integration status",
                    content = @Content(schema = @Schema(implementation = GoogleDriveStatusResponse.class)))
    )
    @PreAuthorize("hasRole('BETA') and (hasRole('USER') or hasAuthority('SCOPE_read:google-drive'))")
    @GetMapping("/status")
    public ResponseEntity<GoogleDriveStatusResponse> getStatus() {
        return ResponseEntity.ok(googleDriveService.getStatus());
    }

    @Operation(summary = "Disconnect Google Drive")
    @PreAuthorize("hasRole('BETA')")
    @DeleteMapping("/connection")
    public ResponseEntity<MessageResponse> disconnect() {
        return ResponseEntity.ok(googleDriveOAuthService.disconnect());
    }

    @Operation(summary = "Update Google Drive root folder")
    @PreAuthorize("hasRole('BETA')")
    @PutMapping("/root-folder")
    public ResponseEntity<GoogleDriveStatusResponse> updateRootFolder(
            @Valid @RequestBody GoogleDriveRootFolderRequest request) {
        return ResponseEntity.ok(googleDriveService.updateRootFolder(request));
    }

    @Operation(
            summary = "Register a Google Docs base resume",
            description = "Registers a Google Docs document as a base resume for the authenticated user. " +
                    "Optionally set the language code and template flag for GPT/frontend discovery."
    )
    @PreAuthorize("hasRole('BETA')")
    @PostMapping("/base-resumes")
    public ResponseEntity<GoogleDriveBaseResumeResponse> addBaseResume(
            @Valid @RequestBody GoogleDriveBaseResumeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(googleDriveService.addBaseResume(request));
    }

    @Operation(
            summary = "List all base resumes for the authenticated user",
            description = "Returns lightweight metadata for all base resumes registered by the authenticated user. " +
                    "Use the returned UUID `id` field in subsequent API calls. " +
                    "Filenames and Google file IDs are NOT valid identifiers for resume operations.",
            responses = @ApiResponse(responseCode = "200", description = "List of base resumes",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = BaseResumeResponse.class))))
    )
    @PreAuthorize("hasRole('BETA') and (hasRole('USER') or hasAuthority('SCOPE_read:resume'))")
    @GetMapping("/base-resumes")
    public ResponseEntity<List<BaseResumeResponse>> listBaseResumes() {
        return ResponseEntity.ok(googleDriveService.listBaseResumes());
    }

    @Operation(summary = "Delete a configured base resume")
    @PreAuthorize("hasRole('BETA')")
    @DeleteMapping("/base-resumes/{baseResumeId}")
    public ResponseEntity<MessageResponse> deleteBaseResume(@PathVariable UUID baseResumeId) {
        googleDriveService.deleteBaseResume(baseResumeId);
        return ResponseEntity.ok(new MessageResponse("Base resume deleted successfully"));
    }

    @Operation(
            summary = "Get plain text content of a base resume",
            description = "Reads and returns the plain text content of the specified Google Docs base resume. " +
                    "Template placeholders such as {{SUMMARY}} and {{SKILLS}} are preserved as-is. " +
                    "The `resumeId` path parameter MUST be a UUID — filenames are NOT valid.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Resume content retrieved",
                            content = @Content(schema = @Schema(implementation = BaseResumeContentResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Base resume not found")
            }
    )
    @PreAuthorize("hasRole('BETA') and (hasRole('USER') or hasAuthority('SCOPE_read:resume'))")
    @GetMapping("/base-resumes/{resumeId}/content")
    public ResponseEntity<BaseResumeContentResponse> getBaseResumeContent(
            @Parameter(description = "UUID of the base resume. NOT the filename.",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID resumeId) {
        return ResponseEntity.ok(resumeGenerationService.getBaseResumeContent(resumeId));
    }

    @Operation(
            summary = "Get plain text content of a generated resume",
            description = "Returns plain text content extracted from the generated Google Docs resume. " +
                    "This endpoint is intended for AI/tool consumption instead of binary file download.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Generated resume content retrieved"),
                    @ApiResponse(responseCode = "404", description = "Application or generated resume not found")
            }
    )
    @PreAuthorize("hasRole('BETA') and (hasRole('USER') or hasAuthority('SCOPE_read:resume'))")
    @GetMapping("/applications/{applicationId}/generated-resumes/content")
    public ResponseEntity<ResumeGenerationService.GeneratedResumeContentResponse> getGeneratedResumeContent(
            @Parameter(description = "UUID of the application.",
                    schema = @Schema(type = "string", format = "uuid"))
            @PathVariable UUID applicationId) {

        return ResponseEntity.ok(
                resumeGenerationService.getGeneratedResumeContent(applicationId)
        );
    }

    @Operation(summary = "Download base resume as DOCX",
            responses = @ApiResponse(
                    responseCode = "200",
                    content = @Content(
                            mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
    )
    @PreAuthorize("hasRole('BETA')")
    @GetMapping("/base-resumes/{baseResumeId}/docx")
    public ResponseEntity<ByteArrayResource> downloadBaseResumeDocx(@PathVariable UUID baseResumeId) {
        return buildDownloadResponse(generatedResumeDownloadService.downloadBaseResumeAsDocx(baseResumeId));
    }

    @Operation(summary = "Download base resume as PDF",
            responses = @ApiResponse(
                    responseCode = "200",
                    content = @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
    )
    @PreAuthorize("hasRole('BETA')")
    @GetMapping("/base-resumes/{baseResumeId}/pdf")
    public ResponseEntity<ByteArrayResource> downloadBaseResumePdf(@PathVariable UUID baseResumeId) {
        return buildDownloadResponse(generatedResumeDownloadService.downloadBaseResumeAsPdf(baseResumeId));
    }

    @Operation(summary = "Copy a base resume into an application folder")
    @PreAuthorize("hasRole('BETA')")
    @PostMapping("/applications/{applicationId}/resume-copies")
    public ResponseEntity<GoogleDriveResumeCopyResponse> copyBaseResume(
            @PathVariable UUID applicationId,
            @Valid @RequestBody GoogleDriveResumeCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(googleDriveService.copyBaseResumeToApplication(applicationId, request));
    }

    @Operation(summary = "Detect placeholders in a configured base resume")
    @PreAuthorize("hasRole('BETA')")
    @PostMapping("/resume-placeholders")
    public ResponseEntity<ResumePlaceholderDetectionResponse> detectResumePlaceholders(
            @Valid @RequestBody ResumePlaceholderDetectionRequest request) {
        return ResponseEntity.ok(resumeGenerationService.detectPlaceholders(request));
    }

    @Operation(summary = "Generate an application resume by replacing template placeholders")
    @PreAuthorize("hasRole('BETA')")
    @PostMapping("/applications/{applicationId}/generated-resumes")
    public ResponseEntity<ResumePlaceholderResponse> generateResume(
            @PathVariable UUID applicationId,
            @Valid @RequestBody ResumePlaceholderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeGenerationService.generateTemplateResume(applicationId, request));
    }

    @Operation(summary = "Download generated resume as DOCX",
            responses = @ApiResponse(
                    responseCode = "200",
                    content = @Content(
                            mediaType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
    )
    @PreAuthorize("hasRole('BETA')")
    @GetMapping("/applications/{applicationId}/generated-resumes/docx")
    public ResponseEntity<ByteArrayResource> downloadGeneratedResumeDocx(@PathVariable UUID applicationId) {
        return buildDownloadResponse(generatedResumeDownloadService.downloadAsDocx(applicationId));
    }

    @Operation(summary = "Download generated resume as PDF",
            responses = @ApiResponse(
                    responseCode = "200",
                    content = @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
    )
    @PreAuthorize("hasRole('BETA')")
    @GetMapping("/applications/{applicationId}/generated-resumes/pdf")
    public ResponseEntity<ByteArrayResource> downloadGeneratedResumePdf(@PathVariable UUID applicationId) {
        return buildDownloadResponse(generatedResumeDownloadService.downloadAsPdf(applicationId));
    }

    private ResponseEntity<ByteArrayResource> buildDownloadResponse(
            GoogleDriveGeneratedResumeDownloadService.DownloadedFile file) {

        ByteArrayResource resource = new ByteArrayResource(file.content());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName())
                                .build()
                                .toString())
                .body(resource);
    }
}
