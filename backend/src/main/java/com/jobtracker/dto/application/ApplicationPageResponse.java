package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of job applications")
public record ApplicationPageResponse(
        @Schema(description = "Applications on this page")
        List<ApplicationResponse> content,
        @Schema(description = "Current page number (0-based)", example = "0")
        int pageNumber,
        @Schema(description = "Number of items per page", example = "10")
        int pageSize,
        @Schema(description = "Total number of applications matching the filter", example = "42")
        long totalElements,
        @Schema(description = "Total number of pages", example = "5")
        int totalPages
) {}
