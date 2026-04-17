package com.jobtracker.integration;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ApplicationRepositoryIT extends AbstractIntegrationTest {

    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setName("Test User");
        user.setEmail("repotest@example.com");
        user.setPasswordHash("hashed");
        user = userRepository.save(user);
    }

    @Test
    void save_shouldPersistApplication() {
        JobApplication app = buildApp("Backend Engineer", user);
        JobApplication saved = applicationRepository.save(app);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVacancyName()).isEqualTo("Backend Engineer");
    }

    @Test
    void findByIdAndUserId_shouldReturnApp_whenExists() {
        JobApplication app = applicationRepository.save(buildApp("Frontend Dev", user));
        Optional<JobApplication> result = applicationRepository.findByIdAndUserId(app.getId(), user.getId());
        assertThat(result).isPresent();
        assertThat(result.get().getVacancyName()).isEqualTo("Frontend Dev");
    }

    @Test
    void findByIdAndUserId_shouldReturnEmpty_whenWrongUser() {
        JobApplication app = applicationRepository.save(buildApp("Frontend Dev", user));
        Optional<JobApplication> result = applicationRepository.findByIdAndUserId(app.getId(), UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void countByUserId_shouldReturnCorrectCount() {
        applicationRepository.save(buildApp("App 1", user));
        applicationRepository.save(buildApp("App 2", user));
        long count = applicationRepository.countByUserId(user.getId());
        assertThat(count).isEqualTo(2);
    }

    @Test
    void findUpcomingByUserId_shouldReturnDmRemindersWithinFirst6Hours() {
        JobApplication app = buildApp("Upcoming", user);
        app.setRecruiterDmReminderEnabled(true);
        applicationRepository.save(app);

        var threshold = LocalDateTime.now().minusHours(6);
        var results = applicationRepository.findUpcomingByUserId(user.getId(), threshold);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVacancyName()).isEqualTo("Upcoming");
    }

    @Test
    void findOverdueByUserId_shouldReturnDmRemindersAfter6Hours() {
        JobApplication app = buildApp("Overdue", user);
        app.setRecruiterDmReminderEnabled(true);
        applicationRepository.save(app);

        var threshold = LocalDateTime.now().plusHours(1);
        var results = applicationRepository.findOverdueByUserId(user.getId(), threshold);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVacancyName()).isEqualTo("Overdue");
    }

    @Test
    void findAll_pageable_shouldReturnPaged() {
        for (int i = 0; i < 5; i++) {
            applicationRepository.save(buildApp("App " + i, user));
        }
        Page<JobApplication> page = applicationRepository.findAll(PageRequest.of(0, 3));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(5);
    }

    private JobApplication buildApp(String vacancyName, User user) {
        JobApplication app = new JobApplication();
        app.setVacancyName(vacancyName);
        app.setOrganization("HR");
        app.setStatus(ApplicationStatus.RH);
        app.setApplicationDate(LocalDate.now().minusDays(1));
        app.setUser(user);
        return app;
    }
}
