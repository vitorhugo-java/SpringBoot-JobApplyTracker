package com.jobtracker.service;

import com.jobtracker.entity.InterviewEvent;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.InterviewEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class InterviewMetricsService {

    private static final Set<ApplicationStatus> INTERVIEW_STATUSES = EnumSet.of(
            ApplicationStatus.ENTREVISTA_MARCADA,
            ApplicationStatus.FIZ_A_RH_AGUARDANDO_ATUALIZACAO,
            ApplicationStatus.FIZ_A_HIRING_MANAGER_AGUARDANDO_ATUALIZACAO,
            ApplicationStatus.TESTE_TECNICO,
            ApplicationStatus.FIZ_TESTE_TECNICO_AGUARDANDO_ATUALIZACAO,
            ApplicationStatus.RH_NEGOCIACAO
    );

    private final ApplicationRepository applicationRepository;
    private final InterviewEventRepository eventRepository;

    public InterviewMetricsService(ApplicationRepository applicationRepository,
                                   InterviewEventRepository eventRepository) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
    }

    public boolean isInterviewStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        try {
            return isInterviewStatus(ApplicationStatus.fromDisplayName(status));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean isInterviewStatus(ApplicationStatus status) {
        return status != null && INTERVIEW_STATUSES.contains(status);
    }

    public boolean wasInterviewTriggered(ApplicationStatus oldStatus, ApplicationStatus newStatus) {
        return isInterviewStatus(newStatus) && !isInterviewStatus(oldStatus);
    }

    @Transactional
    public void recordStatusTransition(JobApplication application,
                                       ApplicationStatus oldStatus,
                                       ApplicationStatus newStatus) {
        if (!wasInterviewTriggered(oldStatus, newStatus)) {
            return;
        }

        application.setInterviewCount(application.getInterviewCount() + 1);

        InterviewEvent event = new InterviewEvent();
        event.setUser(application.getUser());
        event.setApplication(application);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        event.setOccurredAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public long getInterviewCount(UUID userId) {
        return applicationRepository.sumInterviewCountByUserId(userId);
    }
}
