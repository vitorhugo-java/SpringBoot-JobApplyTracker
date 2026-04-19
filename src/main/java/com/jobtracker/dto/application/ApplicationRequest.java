package com.jobtracker.dto.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Request payload for creating or updating a job application")
public record ApplicationRequest(
        @Schema(description = "Job title or vacancy name", example = "Backend Engineer")
        String vacancyName,

        @Schema(description = "Name of the recruiter (optional)", example = "Jane Smith")
        String recruiterName,

                @Schema(description = "Organization or company that posted the vacancy", example = "TechCorp")
                String organization,

        @Schema(description = "URL link to the vacancy posting", example = "https://example.com/jobs/123")
        @Pattern(regexp = "^(https?|ftp)://.*", message = "Vacancy link must be a valid URL")
        String vacancyLink,

        @Schema(description = "Date the application was submitted (yyyy-MM-dd)", example = "2024-06-01")
        @PastOrPresent(message = "Application date cannot be in the future")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate applicationDate,

        @Schema(description = "Whether the recruiter accepted a LinkedIn connection", example = "true")
        @NotNull(message = "rhAcceptedConnection is required")
        Boolean rhAcceptedConnection,

        @Schema(description = "Whether an interview has been scheduled", example = "false")
        @NotNull(message = "interviewScheduled is required")
        Boolean interviewScheduled,

        @Schema(description = "Date/time of the next step (yyyy-MM-dd'T'HH:mm:ss)", example = "2024-06-10T14:00:00")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime nextStepDateTime,

        @Schema(description = "Application status", example = "APPLIED",
                allowableValues = {"RH", "Fiz a RH - Aguardando Atualização", "Fiz a Hiring Manager - Aguardando Atualização", "Teste Técnico", "Fiz teste Técnico - aguardando atualização", "RH (Negociação)", "Rejeitado", "Tarde demais", "Ghosting"})
        String status,

        @Schema(description = "Whether a DM reminder to the recruiter is enabled", example = "true")
        @NotNull(message = "recruiterDmReminderEnabled is required")
        Boolean recruiterDmReminderEnabled,

        @Schema(description = "Personal notes about this application", example = "Follow up next Monday")
        String note
) {}
