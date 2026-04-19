package com.jobtracker.controller;

import com.jobtracker.dto.application.*;
import com.jobtracker.service.ApplicationService;
import com.jobtracker.service.LinkMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Applications", description = "Job application management endpoints")
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final LinkMetadataService linkMetadataService;

    public ApplicationController(ApplicationService applicationService, LinkMetadataService linkMetadataService) {
        this.applicationService = applicationService;
        this.linkMetadataService = linkMetadataService;
    }

    @Operation(
        summary = "Create a job application",
        description = "Creates a new job application for the authenticated user",
        responses = {
            @ApiResponse(responseCode = "201", description = "Application created",
                content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error")
        }
    )
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(request));
    }

    @Operation(
        summary = "Get application by ID",
        description = "Returns a single job application owned by the authenticated user",
        responses = {
            @ApiResponse(responseCode = "200", description = "Application found",
                content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getById(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.getById(id));
    }

    @Operation(
        summary = "Update a job application",
        description = "Replaces all fields of an existing job application",
        responses = {
            @ApiResponse(responseCode = "200", description = "Application updated",
                content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(applicationService.update(id, request));
    }

    @Operation(
        summary = "Update application status",
        description = "Partially updates only the status field of an application",
        responses = {
            @ApiResponse(responseCode = "200", description = "Status updated",
                content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, request));
    }

    @Operation(
        summary = "Update recruiter DM reminder",
        description = "Enables or disables the recruiter DM reminder for an application",
        responses = {
            @ApiResponse(responseCode = "200", description = "Reminder updated",
                content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @PatchMapping("/{id}/reminder")
    public ResponseEntity<ApplicationResponse> updateReminder(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody UpdateReminderRequest request) {
        return ResponseEntity.ok(applicationService.updateReminder(id, request));
    }

    @Operation(
        summary = "Mark DM as sent to recruiter",
        description = "Marks that a DM was sent to the recruiter and hides from reminder panels",
        responses = {
            @ApiResponse(responseCode = "200", description = "DM marked as sent",
                content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @PatchMapping("/{id}/mark-dm-sent")
    public ResponseEntity<ApplicationResponse> markDmSent(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id,
            @Valid @RequestBody MarkDmSentRequest request) {
        return ResponseEntity.ok(applicationService.markDmSent(id, request));
    }

    @Operation(
        summary = "Delete a job application permanently",
        responses = {
            @ApiResponse(responseCode = "200", description = "Application deleted"),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id) {
        applicationService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Application deleted successfully"));
    }

    @Operation(
        summary = "Archive a job application",
        responses = {
            @ApiResponse(responseCode = "200", description = "Application archived",
                    content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found")
        }
    )
    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApplicationResponse> archive(
            @Parameter(description = "Application ID", required = true) @PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.archive(id));
    }

    @Operation(
        summary = "List job applications",
        description = "Returns a paginated, filterable list of job applications for the authenticated user",
        responses = {
            @ApiResponse(responseCode = "200", description = "Page of applications",
                content = @Content(schema = @Schema(implementation = ApplicationPageResponse.class)))
        }
    )
    @GetMapping
    public ResponseEntity<ApplicationPageResponse> getAll(
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by recruiter name") @RequestParam(required = false) String recruiterName,
            @Parameter(description = "Filter from date (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateFrom,
            @Parameter(description = "Filter to date (yyyy-MM-dd)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateTo,
            @Parameter(description = "Filter by interview scheduled flag") @RequestParam(required = false) Boolean interviewScheduled,
            @Parameter(description = "Filter by recruiter DM reminder flag") @RequestParam(required = false) Boolean recruiterDmReminderEnabled,
            @Parameter(description = "Filter by archived flag (defaults to false)") @RequestParam(required = false) Boolean archived,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(applicationService.getAll(status, recruiterName, applicationDateFrom,
                applicationDateTo, interviewScheduled, recruiterDmReminderEnabled, archived, page, size, sort));
    }

    @Operation(
        summary = "Get upcoming applications",
        description = "Returns applications with upcoming next-step dates",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of upcoming applications")
        }
    )
    @GetMapping("/upcoming")
    public ResponseEntity<List<ApplicationResponse>> getUpcoming() {
        return ResponseEntity.ok(applicationService.getUpcoming());
    }

    @Operation(
        summary = "Get overdue applications",
        description = "Returns applications whose next-step date has already passed",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of overdue applications")
        }
    )
    @GetMapping("/overdue")
    public ResponseEntity<List<ApplicationResponse>> getOverdue() {
        return ResponseEntity.ok(applicationService.getOverdue());
    }

    @Operation(
        summary = "Extract link metadata",
        description = "Extracts rich preview metadata (title, description, image, domain) from a URL",
        responses = {
            @ApiResponse(responseCode = "200", description = "Link metadata extracted",
                content = @Content(schema = @Schema(implementation = LinkMetadataResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL")
        }
    )
    @GetMapping("/link-metadata")
    public ResponseEntity<LinkMetadataResponse> getLinkMetadata(
            @Parameter(description = "URL to extract metadata from", required = true) @RequestParam String url) {
        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        LinkMetadataResponse metadata = linkMetadataService.extractMetadata(url);
        return ResponseEntity.ok(metadata);
    }
}
