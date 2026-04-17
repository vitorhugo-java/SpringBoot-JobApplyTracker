package com.jobtracker.dto.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to update the status of a job application")
public record UpdateStatusRequest(
        @Schema(description = "New application status", example = "INTERVIEW",
                allowableValues = {"APPLIED", "IN_REVIEW", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN"})
        String status
) {}
