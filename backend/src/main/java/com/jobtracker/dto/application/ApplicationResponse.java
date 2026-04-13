package com.jobtracker.dto.application;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        String vacancyName,
        String recruiterName,
        String vacancyOpenedBy,
        String vacancyLink,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate applicationDate,
        boolean rhAcceptedConnection,
        boolean interviewScheduled,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime nextStepDateTime,
        String status,
        boolean recruiterDmReminderEnabled,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {}
