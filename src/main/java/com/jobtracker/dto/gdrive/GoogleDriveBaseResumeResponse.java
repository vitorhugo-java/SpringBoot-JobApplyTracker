package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Configured Google Docs base resume")
public record GoogleDriveBaseResumeResponse(
        UUID id,
        String googleFileId,
        String documentName,
        String webViewLink,
        LocalDateTime createdAt
) {}
