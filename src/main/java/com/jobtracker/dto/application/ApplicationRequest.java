package com.jobtracker.dto.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApplicationRequest(
        @NotBlank(message = "Vacancy name is required")
        String vacancyName,

        String recruiterName,

        @NotBlank(message = "Vacancy opened by is required")
        String vacancyOpenedBy,

        @Pattern(regexp = "^(https?|ftp)://.*", message = "Vacancy link must be a valid URL")
        String vacancyLink,

        @NotNull(message = "Application date is required")
        @PastOrPresent(message = "Application date cannot be in the future")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate applicationDate,

        @NotNull(message = "rhAcceptedConnection is required")
        Boolean rhAcceptedConnection,

        @NotNull(message = "interviewScheduled is required")
        Boolean interviewScheduled,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime nextStepDateTime,

        @NotBlank(message = "Status is required")
        String status,

        @NotNull(message = "recruiterDmReminderEnabled is required")
        Boolean recruiterDmReminderEnabled
) {}
