package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Current Google Drive integration status")
public record GoogleDriveStatusResponse(
        boolean configured,
        boolean connected,
        String googleEmail,
        String googleDisplayName,
        String googleAccountId,
        String rootFolderId,
        String rootFolderName,
        LocalDateTime connectedAt,
        List<GoogleDriveBaseResumeResponse> baseResumes
) {}
