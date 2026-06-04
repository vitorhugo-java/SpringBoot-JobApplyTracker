package com.jobtracker.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.mcp.McpResourcesConfig;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GamificationService;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.util.SecurityUtils;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class McpReadOnlySnapshotResources {

    private final DashboardService dashboardService;
    private final GamificationService gamificationService;
    private final GoogleDriveService googleDriveService;
    private final AuthMapper authMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    public McpReadOnlySnapshotResources(DashboardService dashboardService,
                                        GamificationService gamificationService,
                                        GoogleDriveService googleDriveService,
                                        AuthMapper authMapper,
                                        SecurityUtils securityUtils,
                                        ObjectMapper objectMapper) {
        this.dashboardService = dashboardService;
        this.gamificationService = gamificationService;
        this.googleDriveService = googleDriveService;
        this.authMapper = authMapper;
        this.securityUtils = securityUtils;
        this.objectMapper = objectMapper;
    }

    @McpResource(
            uri = McpResourcesConfig.URI_PIPELINE_SUMMARY,
            name = "Pipeline Summary",
            mimeType = "application/json")
    public String pipelineSummary() {
        return toJson(dashboardService.getSummary());
    }

    @McpResource(
            uri = McpResourcesConfig.URI_GAMIFICATION_PROFILE,
            name = "Gamification Profile",
            mimeType = "application/json")
    public String gamificationProfile() {
        return toJson(gamificationService.getProfile());
    }

    @McpResource(
            uri = McpResourcesConfig.URI_ACHIEVEMENTS,
            name = "Achievements",
            mimeType = "application/json")
    public String achievements() {
        return toJson(gamificationService.getAchievements());
    }

    @PreAuthorize("hasRole('BETA')")
    @McpResource(
            uri = McpResourcesConfig.URI_DRIVE_STATUS,
            name = "Drive Status",
            mimeType = "application/json")
    public String driveStatus() {
        return toJson(googleDriveService.getStatus());
    }

    @PreAuthorize("hasRole('BETA')")
    @McpResource(
            uri = McpResourcesConfig.URI_BASE_RESUMES,
            name = "Base Resumes",
            mimeType = "application/json")
    public String baseResumes() {
        return toJson(googleDriveService.listBaseResumes());
    }

    @McpResource(
            uri = McpResourcesConfig.URI_CURRENT_USER,
            name = "Current User",
            mimeType = "application/json")
    public String currentUser() {
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
