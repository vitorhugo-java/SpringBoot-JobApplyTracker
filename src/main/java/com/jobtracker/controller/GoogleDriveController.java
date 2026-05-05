package com.jobtracker.controller;

import com.jobtracker.dto.auth.MessageResponse;
import com.jobtracker.dto.gdrive.*;
import com.jobtracker.service.GoogleDriveOAuthService;
import com.jobtracker.service.GoogleDriveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Tag(name = "Google Drive", description = "Google Drive OAuth and resume copy endpoints")
@RestController
@RequestMapping("/api/v1/google-drive")
public class GoogleDriveController {

    private final GoogleDriveOAuthService googleDriveOAuthService;
    private final GoogleDriveService googleDriveService;

    public GoogleDriveController(GoogleDriveOAuthService googleDriveOAuthService, GoogleDriveService googleDriveService) {
        this.googleDriveOAuthService = googleDriveOAuthService;
        this.googleDriveService = googleDriveService;
    }

    @Operation(
            summary = "Start Google Drive OAuth flow",
            responses = @ApiResponse(responseCode = "200", description = "Authorization URL generated",
                    content = @Content(schema = @Schema(implementation = GoogleDriveOAuthStartResponse.class)))
    )
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
    @GetMapping("/status")
    public ResponseEntity<GoogleDriveStatusResponse> getStatus() {
        return ResponseEntity.ok(googleDriveService.getStatus());
    }

    @Operation(summary = "Disconnect Google Drive")
    @DeleteMapping("/connection")
    public ResponseEntity<MessageResponse> disconnect() {
        return ResponseEntity.ok(googleDriveOAuthService.disconnect());
    }

    @Operation(summary = "Update Google Drive root folder")
    @PutMapping("/root-folder")
    public ResponseEntity<GoogleDriveStatusResponse> updateRootFolder(
            @Valid @RequestBody GoogleDriveRootFolderRequest request) {
        return ResponseEntity.ok(googleDriveService.updateRootFolder(request));
    }

    @Operation(summary = "Register a Google Docs base resume")
    @PostMapping("/base-resumes")
    public ResponseEntity<GoogleDriveBaseResumeResponse> addBaseResume(
            @Valid @RequestBody GoogleDriveBaseResumeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(googleDriveService.addBaseResume(request));
    }

    @Operation(summary = "Delete a configured base resume")
    @DeleteMapping("/base-resumes/{baseResumeId}")
    public ResponseEntity<MessageResponse> deleteBaseResume(@PathVariable UUID baseResumeId) {
        googleDriveService.deleteBaseResume(baseResumeId);
        return ResponseEntity.ok(new MessageResponse("Base resume deleted successfully"));
    }

    @Operation(summary = "Copy a base resume into an application folder")
    @PostMapping("/applications/{applicationId}/resume-copies")
    public ResponseEntity<GoogleDriveResumeCopyResponse> copyBaseResume(
            @PathVariable UUID applicationId,
            @Valid @RequestBody GoogleDriveResumeCopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(googleDriveService.copyBaseResumeToApplication(applicationId, request));
    }
}
