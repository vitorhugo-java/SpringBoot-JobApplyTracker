package com.jobtracker.service;

import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

        long totalApplications = applicationRepository.countByUserIdAndArchivedFalse(userId);

        List<ApplicationStatus> waitingStatuses = List.of(
                ApplicationStatus.FIZ_A_RH_AGUARDANDO_ATUALIZACAO,
                ApplicationStatus.FIZ_A_HIRING_MANAGER_AGUARDANDO_ATUALIZACAO,
                ApplicationStatus.FIZ_TESTE_TECNICO_AGUARDANDO_ATUALIZACAO
        );
        long waitingResponses = applicationRepository.countByUserIdAndStatusInAndArchivedFalse(userId, waitingStatuses);

        long interviewsScheduled = applicationRepository.countByUserIdAndInterviewScheduledTrueAndArchivedFalse(userId);

        long overdueFollowUps = applicationRepository.findOverdueByUserId(userId, LocalDateTime.now().minusHours(6), LocalDateTime.now().minusDays(2)).size();

        long dmRemindersEnabled = applicationRepository.countPendingDmRemindersByUserId(userId);

        long toSendLater = applicationRepository.countByUserIdAndStatusIsNullAndArchivedFalse(userId);

        long rejectedCount = applicationRepository.countByUserIdAndStatusAndArchivedFalse(userId, ApplicationStatus.REJEITADO);

        long ghostingCount = applicationRepository.countByUserIdAndStatusAndArchivedFalse(userId, ApplicationStatus.GHOSTING);

        double averageDailyApplications = roundToTwoDecimals(
            applicationRepository.countByUserIdAndApplicationDateSince(userId, LocalDate.now().minusDays(29)) / 30.0
        );
        double averageWeeklyApplications = roundToTwoDecimals(
            applicationRepository.countByUserIdAndApplicationDateSince(userId, LocalDate.now().minusWeeks(11)) / 12.0
        );
        double averageMonthlyApplications = roundToTwoDecimals(
            applicationRepository.countByUserIdAndApplicationDateSince(userId, LocalDate.now().minusMonths(11)) / 12.0
        );

        return new DashboardSummaryResponse(
                totalApplications,
                waitingResponses,
                interviewsScheduled,
                overdueFollowUps,
                dmRemindersEnabled,
                toSendLater,
                rejectedCount,
                ghostingCount,
                averageDailyApplications,
                averageWeeklyApplications,
                averageMonthlyApplications
        );
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
