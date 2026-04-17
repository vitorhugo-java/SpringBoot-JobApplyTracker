package com.jobtracker.tasks;

import com.jobtracker.entity.User;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.UserRepository;
import com.jobtracker.service.EmailService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;

@Component
public class PendingApplicationsReminderScheduler {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;

    public PendingApplicationsReminderScheduler(UserRepository userRepository,
                                                ApplicationRepository applicationRepository,
                                                EmailService emailService) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendDailyPendingApplicationReminders() {
        LocalTime currentMinute = LocalTime.now().withSecond(0).withNano(0);
        List<User> usersToRemind = userRepository.findByReminderTime(currentMinute);
        for (User user : usersToRemind) {
            long pendingCount = applicationRepository.countByUserIdAndStatusIsNull(user.getId());
            if (pendingCount > 0) {
                emailService.sendPendingApplicationsReminderEmail(user, pendingCount);
            }
        }
    }
}
