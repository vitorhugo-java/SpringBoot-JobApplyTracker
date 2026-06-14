package com.jobtracker.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.BaseInformationService;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GamificationService;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.util.SecurityUtils;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springaicommunity.mcp.annotation.McpResource.McpAnnotations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class McpReadOnlySnapshotResources {

    private static final String LAST_MODIFIED = "2026-06-04";

    private final DashboardService dashboardService;
    private final GamificationService gamificationService;
    private final GoogleDriveService googleDriveService;
    private final BaseInformationService baseInformationService;
    private final AuthMapper authMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    public McpReadOnlySnapshotResources(DashboardService dashboardService,
                                        GamificationService gamificationService,
                                        GoogleDriveService googleDriveService,
                                        BaseInformationService baseInformationService,
                                        AuthMapper authMapper,
                                        SecurityUtils securityUtils,
                                        ObjectMapper objectMapper) {
        this.dashboardService = dashboardService;
        this.gamificationService = gamificationService;
        this.googleDriveService = googleDriveService;
        this.baseInformationService = baseInformationService;
        this.authMapper = authMapper;
        this.securityUtils = securityUtils;
        this.objectMapper = objectMapper;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_PIPELINE_SUMMARY,
            name = "Pipeline Summary",
            title = "Pipeline Summary",
            description = "JSON snapshot of application counts, status breakdown, reminders, and activity rate.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 1.0d))
    @AuditMcpOperation(action = "Pipeline Summary")
    public String pipelineSummary(McpSyncServerExchange exchange) {
        return toJson(dashboardService.getSummary());
    }

    @McpResource(
            uri = McpResourcesConfig.URI_GAMIFICATION_PROFILE,
            name = "Gamification Profile",
            title = "Gamification Profile",
            description = "JSON snapshot of the current user's gamification state.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.7d))
    @AuditMcpOperation(action = "Gamification Profile")
    public String gamificationProfile(McpSyncServerExchange exchange) {
        return toJson(gamificationService.getProfile());
    }

    @McpResource(
            uri = McpResourcesConfig.URI_ACHIEVEMENTS,
            name = "Achievements",
            title = "Achievements",
            description = "JSON list of achievement progress and unlock timestamps.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.6d))
    @AuditMcpOperation(action = "Achievements")
    public String achievements(McpSyncServerExchange exchange) {
        return toJson(gamificationService.getAchievements());
    }

    @PreAuthorize("hasRole('BETA')")
    @McpResource(
            uri = McpResourcesConfig.URI_DRIVE_STATUS,
            name = "Drive Status",
            title = "Drive Status",
            description = "JSON status of the current user's Google Drive connection and configuration.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.5d))
    @AuditMcpOperation(action = "Drive Status")
    public String driveStatus(McpSyncServerExchange exchange) {
        return toJson(googleDriveService.getStatus());
    }

    @PreAuthorize("hasRole('BETA')")
    @McpResource(
            uri = McpResourcesConfig.URI_BASE_RESUMES,
            name = "Base Resumes",
            title = "Base Resumes",
            description = "JSON list of configured Google Docs base resume templates. Each entry includes the UUID (id) required by Copy-Resume-To-Application, Generate-Resume, and Detect-Resume-Placeholders; the display name; the language code (e.g. PT, EN) used to select the correct template for the vacancy language; and whether it is a reusable placeholder template. Alternatively, call the List-Base-Resumes tool to fetch the same data.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.9d))
    @AuditMcpOperation(action = "Base Resumes")
    public String baseResumes(McpSyncServerExchange exchange) {
        return toJson(googleDriveService.listBaseResumes());
    }

    @PreAuthorize("hasRole('BETA')")
    @McpResource(
            uri = McpResourcesConfig.URI_BASE_INFORMATION,
            name = "Base Information",
            title = "Base Information",
            description = "JSON list of the candidate's base information documents (Google Docs, PDF, DOCX, or Markdown). " +
                    "This is the authoritative, highest-priority source of truth about the candidate. Each entry includes the UUID (id) " +
                    "required by Get-Base-Information-Content, the display name, and the docType. Read every document before generating any CV content. " +
                    "Alternatively, call the List-Base-Information tool to fetch the same data.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.95d))
    @AuditMcpOperation(action = "Base Information")
    public String baseInformation(McpSyncServerExchange exchange) {
        return toJson(baseInformationService.listBaseInformation());
    }

    @McpResource(
            uri = McpResourcesConfig.URI_CURRENT_USER,
            name = "Current User",
            title = "Current User",
            description = "JSON view of the authenticated user profile.",
            mimeType = "application/json",
            annotations = @McpAnnotations(
                    audience = {Role.USER, Role.ASSISTANT},
                    lastModified = LAST_MODIFIED,
                    priority = 0.8d))
    @AuditMcpOperation(action = "Current User")
    public String currentUser(McpSyncServerExchange exchange) {
        UserResponse response = authMapper.toUserResponse(securityUtils.getCurrentUser());
        return toJson(response);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MCP resource", ex);
        }
    }
}
