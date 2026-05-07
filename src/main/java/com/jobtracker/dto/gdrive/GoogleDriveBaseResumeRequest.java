package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to register a Google Docs base resume")
public record GoogleDriveBaseResumeRequest(
        @Schema(description = "Google Docs document ID or URL", example = "https://docs.google.com/document/d/1234567890abcdef/edit")
        @NotBlank(message = "documentIdOrUrl is required")
        String documentIdOrUrl
) {}
