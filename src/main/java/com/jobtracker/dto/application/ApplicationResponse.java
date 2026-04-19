package com.jobtracker.dto.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Job application details")
public record ApplicationResponse(
        @Schema(description = "Unique application ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,
        @Schema(description = "Job title or vacancy name", example = "Backend Engineer")
        String vacancyName,
        @Schema(description = "Recruiter name", example = "Jane Smith")
        String recruiterName,
        @Schema(description = "Organization or company that posted the vacancy", example = "TechCorp HR")
        String organization,
        @Schema(description = "URL to the vacancy posting", example = "https://example.com/jobs/123")
        String vacancyLink,
        @Schema(description = "Application date (yyyy-MM-dd)", example = "2024-06-01")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate applicationDate,
        @Schema(description = "Whether the recruiter accepted a LinkedIn connection", example = "true")
        boolean rhAcceptedConnection,
        @Schema(description = "Whether an interview has been scheduled", example = "false")
        boolean interviewScheduled,
        @Schema(description = "Next step date/time", example = "2024-06-10T14:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime nextStepDateTime,
        @Schema(description = "Application status", example = "APPLIED")
        String status,
        @Schema(description = "Previous application status before Rejeitado or Ghosting", example = "Teste Técnico")
        String previousStatus,
        @Schema(description = "Whether a recruiter DM reminder is enabled", example = "true")
        boolean recruiterDmReminderEnabled,
        @Schema(description = "Timestamp when DM was sent to the recruiter", example = "2024-06-05T14:30:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime recruiterDmSentAt,
        @Schema(description = "Personal notes about this application", example = "Follow up next Monday")
        String note,
        @Schema(description = "Whether the application is archived", example = "false")
        boolean archived,
        @Schema(description = "Timestamp when application was archived", example = "2024-06-10T09:15:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime archivedAt,
        @Schema(description = "Record creation timestamp", example = "2024-06-01T10:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        @Schema(description = "Last update timestamp", example = "2024-06-05T12:30:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {}
