package com.jobtracker.service;

import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public DashboardService(ApplicationRepository applicationRepository, SecurityUtils securityUtils) {
        this.applicationRepository = applicationRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        UUID userId = securityUtils.getCurrentUserId();

        long totalApplications = applicationRepository.countByUserId(userId);

        List<ApplicationStatus> waitingStatuses = List.of(
                ApplicationStatus.FIZ_A_RH_AGUARDANDO_ATUALIZACAO,
                ApplicationStatus.FIZ_A_HIRING_MANAGER_AGUARDANDO_ATUALIZACAO,
                ApplicationStatus.FIZ_TESTE_TECNICO_AGUARDANDO_ATUALIZACAO
        );
        long waitingResponses = applicationRepository.countByUserIdAndStatusIn(userId, waitingStatuses);

        long interviewsScheduled = applicationRepository.countByUserIdAndInterviewScheduledTrue(userId);

        long overdueFollowUps = applicationRepository.findOverdueByUserId(userId, LocalDateTime.now()).size();

        long dmRemindersEnabled = applicationRepository.countByUserIdAndRecruiterDmReminderEnabledTrue(userId);

        return new DashboardSummaryResponse(
                totalApplications,
                waitingResponses,
                interviewsScheduled,
                overdueFollowUps,
                dmRemindersEnabled
        );
    }
}
