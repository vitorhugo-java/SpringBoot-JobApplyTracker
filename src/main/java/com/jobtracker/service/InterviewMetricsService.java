package com.jobtracker.service;

import com.jobtracker.entity.InterviewEvent;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.UserInterviewMetrics;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.repository.UserRepository;
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

    private final UserInterviewMetricsRepository metricsRepository;
    private final InterviewEventRepository eventRepository;
    private final UserRepository userRepository;

    public InterviewMetricsService(UserInterviewMetricsRepository metricsRepository,
                                   InterviewEventRepository eventRepository,
                                   UserRepository userRepository) {
        this.metricsRepository = metricsRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
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

        User user = application.getUser();
        UserInterviewMetrics metrics = findOrCreateMetrics(user);
        metrics.setInterviewCount(metrics.getInterviewCount() + 1);
        metricsRepository.save(metrics);

        InterviewEvent event = new InterviewEvent();
        event.setUser(user);
        event.setApplication(application);
        event.setOldStatus(oldStatus);
        event.setNewStatus(newStatus);
        event.setOccurredAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    @Transactional
    public void setInterviewCount(UUID userId, long count) {
        if (count < 0) throw new IllegalArgumentException("Interview count cannot be negative");
        UserInterviewMetrics metrics = metricsRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.getReferenceById(userId);
                    UserInterviewMetrics created = new UserInterviewMetrics();
                    created.setUser(user);
                    created.setInterviewCount(0);
                    return created;
                });
        metrics.setInterviewCount(count);
        metricsRepository.save(metrics);
    }

    @Transactional(readOnly = true)
    public long getInterviewCount(UUID userId) {
        return metricsRepository.findById(userId)
                .map(UserInterviewMetrics::getInterviewCount)
                .orElseGet(() -> eventRepository.countByUser_Id(userId));
    }

    private UserInterviewMetrics findOrCreateMetrics(User user) {
        return metricsRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    UserInterviewMetrics created = new UserInterviewMetrics();
                    created.setUser(user);
                    created.setInterviewCount(0);
                    return created;
                });
    }
}
