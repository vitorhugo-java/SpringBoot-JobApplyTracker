package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to configure the user's Google Drive root folder")
public record GoogleDriveRootFolderRequest(
        @Schema(description = "Google Drive folder ID or URL", example = "https://drive.google.com/drive/folders/1234567890abcdef")
        @NotBlank(message = "folderIdOrUrl is required")
        String folderIdOrUrl
) {}
