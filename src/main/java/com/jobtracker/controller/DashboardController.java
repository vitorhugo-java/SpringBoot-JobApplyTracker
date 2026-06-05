package com.jobtracker.controller;

import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.dto.dashboard.UpdateInterviewCountRequest;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.InterviewMetricsService;
import com.jobtracker.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Dashboard summary statistics endpoints")
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final InterviewMetricsService interviewMetricsService;
    private final SecurityUtils securityUtils;

    public DashboardController(DashboardService dashboardService,
                               InterviewMetricsService interviewMetricsService,
                               SecurityUtils securityUtils) {
        this.dashboardService = dashboardService;
        this.interviewMetricsService = interviewMetricsService;
        this.securityUtils = securityUtils;
    }

    @Operation(
        summary = "Get dashboard summary",
        description = "Returns aggregate statistics for the authenticated user's job applications",
        responses = {
            @ApiResponse(responseCode = "200", description = "Dashboard summary",
                content = @Content(schema = @Schema(implementation = DashboardSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @Operation(
        summary = "Update interview count",
        description = "Manually sets the cumulative interview count for the authenticated user",
        responses = {
            @ApiResponse(responseCode = "204", description = "Count updated"),
            @ApiResponse(responseCode = "400", description = "Invalid count value"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @PatchMapping("/interview-count")
    public ResponseEntity<Void> updateInterviewCount(@Valid @RequestBody UpdateInterviewCountRequest request) {
        interviewMetricsService.setInterviewCount(securityUtils.getCurrentUserId(), request.count());
        return ResponseEntity.noContent().build();
    }
}
