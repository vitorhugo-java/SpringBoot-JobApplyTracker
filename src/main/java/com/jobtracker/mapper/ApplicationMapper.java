package com.jobtracker.mapper;

import com.jobtracker.dto.application.ApplicationResponse;
import com.jobtracker.entity.JobApplication;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    public ApplicationResponse toResponse(JobApplication app) {
        return new ApplicationResponse(
            app.getId(),
            app.getVacancyName(),
            app.getRecruiterName(),
            app.getOrganization(),
            app.getVacancyLink(),
            app.getApplicationDate(),
            app.isRhAcceptedConnection(),
            app.isInterviewScheduled(),
            app.getNextStepDateTime(),
            app.getStatus() != null ? app.getStatus().getDisplayName() : null,
            app.getPreviousStatus() != null ? app.getPreviousStatus().getDisplayName() : null,
            app.isRecruiterDmReminderEnabled(),
            app.getCreatedAt(),
            app.getUpdatedAt()
        );
    }
}
