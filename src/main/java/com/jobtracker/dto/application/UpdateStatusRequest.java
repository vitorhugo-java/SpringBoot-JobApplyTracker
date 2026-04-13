package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to update the status of a job application")
public record UpdateStatusRequest(
        @Schema(description = "New application status", example = "INTERVIEW",
                allowableValues = {"APPLIED", "IN_REVIEW", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN"})
        @NotBlank(message = "Status is required")
        String status
) {}
