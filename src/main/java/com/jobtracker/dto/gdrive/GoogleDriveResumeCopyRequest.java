package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request to copy a configured base resume into an application folder")
public record GoogleDriveResumeCopyRequest(
        @Schema(description = "Configured base resume identifier")
        @NotNull(message = "baseResumeId is required")
        UUID baseResumeId
) {}
