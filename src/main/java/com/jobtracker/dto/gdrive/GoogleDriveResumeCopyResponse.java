package com.jobtracker.dto.gdrive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Result of copying a Google Docs resume into Drive")
public record GoogleDriveResumeCopyResponse(
        UUID applicationId,
        UUID baseResumeId,
        String copiedFileId,
        String copiedFileName,
        String documentWebViewLink,
        String vacancyFolderId,
        String vacancyFolderName,
        String vacancyFolderWebViewLink
) {}
