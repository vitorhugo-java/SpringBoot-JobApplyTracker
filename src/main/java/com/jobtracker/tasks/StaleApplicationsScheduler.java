package com.jobtracker.tasks;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class StaleApplicationsScheduler {

    private final ApplicationRepository applicationRepository;

    public StaleApplicationsScheduler(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void updateStaleApplications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneWeekAgo = now.minusWeeks(1);
        LocalDateTime oneMonthAgo = now.minusMonths(1);

        List<JobApplication> toSendLaterExpired = applicationRepository.findByStatusIsNullAndUpdatedAtBefore(oneWeekAgo);
        for (JobApplication application : toSendLaterExpired) {
            application.setPreviousStatus(null);
            application.setStatus(ApplicationStatus.TARDE_DEMAIS);
        }
        if (!toSendLaterExpired.isEmpty()) {
            applicationRepository.saveAll(toSendLaterExpired);
        }

        List<JobApplication> staleApplications = applicationRepository
                .findByStatusIsNotNullAndStatusNotAndUpdatedAtBefore(ApplicationStatus.GHOSTING, oneMonthAgo);
        for (JobApplication application : staleApplications) {
            ApplicationStatus currentStatus = application.getStatus();
            if (currentStatus != ApplicationStatus.GHOSTING) {
                application.setPreviousStatus(currentStatus);
                application.setStatus(ApplicationStatus.GHOSTING);
            }
        }
        if (!staleApplications.isEmpty()) {
            applicationRepository.saveAll(staleApplications);
        }
    }
}
