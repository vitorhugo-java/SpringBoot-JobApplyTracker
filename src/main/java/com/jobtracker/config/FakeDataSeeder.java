package com.jobtracker.config;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.UserRepository;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class FakeDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FakeDataSeeder.class);

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final Faker faker = new Faker(new Locale("pt", "BR"));

    @Value("${app.seed.user-email:}")
    private String targetUserEmail;

    @Value("${app.seed.count:1000}")
    private int seedCount;

    public FakeDataSeeder(UserRepository userRepository, ApplicationRepository applicationRepository) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (targetUserEmail == null || targetUserEmail.isBlank()) {
            throw new IllegalStateException("app.seed.user-email is required when app.seed.enabled=true");
        }
        if (seedCount < 1) {
            throw new IllegalStateException("app.seed.count must be greater than 0");
        }

        User user = userRepository.findByEmail(targetUserEmail)
                .orElseThrow(() -> new IllegalStateException("User not found for email: " + targetUserEmail));

        List<JobApplication> applications = new ArrayList<>(seedCount);
        for (int i = 0; i < seedCount; i++) {
            applications.add(buildFakeApplication(user));
        }

        applicationRepository.saveAll(applications);
        log.info("event=SEED_SUCCESS userEmail={} recordsInserted={}", targetUserEmail, seedCount);
    }

    private JobApplication buildFakeApplication(User user) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        boolean interviewScheduled = random.nextBoolean();
        boolean reminderEnabled = random.nextBoolean();

        JobApplication application = new JobApplication();
        application.setUser(user);
        application.setVacancyName(faker.job().position());
        application.setRecruiterName(faker.name().fullName());
        application.setOrganization(faker.company().name());
        application.setVacancyLink("https://www.linkedin.com/jobs/view/" + faker.number().digits(10));
        application.setApplicationDate(LocalDate.now().minusDays(random.nextInt(1, 365)));
        application.setRhAcceptedConnection(random.nextBoolean());
        application.setInterviewScheduled(interviewScheduled);
        application.setStatus(randomStatus());
        application.setRecruiterDmReminderEnabled(reminderEnabled);

        if (interviewScheduled) {
            LocalDateTime nextStep = LocalDateTime.now()
                    .plusDays(random.nextInt(1, 60))
                    .withHour(random.nextInt(9, 19))
                    .withMinute(random.nextInt(0, 4) * 15)
                    .withSecond(0)
                    .withNano(0);
            application.setNextStepDateTime(nextStep);
        }

        return application;
    }

    private ApplicationStatus randomStatus() {
        ApplicationStatus[] statuses = ApplicationStatus.values();
        return statuses[ThreadLocalRandom.current().nextInt(statuses.length)];
    }
}
