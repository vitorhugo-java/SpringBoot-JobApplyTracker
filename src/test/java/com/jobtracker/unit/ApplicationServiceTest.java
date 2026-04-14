package com.jobtracker.unit;

import com.jobtracker.dto.application.*;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.mapper.ApplicationMapper;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.service.ApplicationService;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final UUID USER_UUID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID APP_UUID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private SecurityUtils securityUtils;
    @Mock(answer = RETURNS_DEEP_STUBS) private Tracer tracer;

    @InjectMocks
    private ApplicationService applicationService;

    private User user;
    private JobApplication app;
    private ApplicationResponse response;

    @BeforeEach
    void setUp() {
        user = buildUser(USER_UUID, "user@example.com");
        app = buildApplication(APP_UUID, user);
        response = buildApplicationResponse(APP_UUID);
    }

    @Test
    void create_shouldSaveAndReturnResponse() {
        ApplicationRequest request = buildRequest();
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(applicationRepository.save(any(JobApplication.class))).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.create(request);
        assertThat(result.id()).isEqualTo(APP_UUID);
        verify(applicationRepository).save(any(JobApplication.class));
    }

    @Test
    void getById_shouldReturnApplication_whenFoundAndBelongsToUser() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(APP_UUID, USER_UUID)).thenReturn(Optional.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.getById(APP_UUID);
        assertThat(result.id()).isEqualTo(APP_UUID);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(OTHER_UUID, USER_UUID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.getById(OTHER_UUID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_shouldUpdateAndReturnResponse() {
        ApplicationRequest request = buildRequest();
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(APP_UUID, USER_UUID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.update(APP_UUID, request);
        assertThat(result).isNotNull();
    }

    @Test
    void updateStatus_shouldUpdateStatus() {
        UpdateStatusRequest statusRequest = new UpdateStatusRequest("RH");
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(APP_UUID, USER_UUID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.updateStatus(APP_UUID, statusRequest);
        assertThat(result).isNotNull();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.RH);
    }

    @Test
    void updateStatus_shouldThrow_whenInvalidStatus() {
        UpdateStatusRequest statusRequest = new UpdateStatusRequest("INVALID_STATUS");
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(APP_UUID, USER_UUID)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> applicationService.updateStatus(APP_UUID, statusRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status value");
    }

    @Test
    void updateReminder_shouldToggleReminder() {
        UpdateReminderRequest reminderRequest = new UpdateReminderRequest(true);
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(APP_UUID, USER_UUID)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        applicationService.updateReminder(APP_UUID, reminderRequest);
        assertThat(app.isRecruiterDmReminderEnabled()).isTrue();
    }

    @Test
    void delete_shouldDeleteApplication() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(APP_UUID, USER_UUID)).thenReturn(Optional.of(app));

        applicationService.delete(APP_UUID);
        verify(applicationRepository).delete(app);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findByIdAndUserId(OTHER_UUID, USER_UUID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.delete(OTHER_UUID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAll_shouldReturnPagedResponse() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        PageImpl<JobApplication> page = new PageImpl<>(List.of(app));
        when(applicationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationPageResponse result = applicationService.getAll(null, null, null, null, null, null, 0, 10, null);
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getAll_shouldThrow_whenInvalidSortField() {
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        assertThatThrownBy(() -> applicationService.getAll(null, null, null, null, null, null, 0, 10, "invalidField,asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid sort field");
    }

    @Test
    void getUpcoming_shouldReturnList() {
        LocalDateTime before = LocalDateTime.now();
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findUpcomingByUserId(eq(USER_UUID), any(LocalDateTime.class)))
                .thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        List<ApplicationResponse> result = applicationService.getUpcoming();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(applicationRepository).findUpcomingByUserId(eq(USER_UUID), thresholdCaptor.capture());

        LocalDateTime expectedMin = before.minusHours(6).minusSeconds(1);
        LocalDateTime expectedMax = after.minusHours(6).plusSeconds(1);
        assertThat(thresholdCaptor.getValue()).isBetween(expectedMin, expectedMax);
        assertThat(result).hasSize(1);
    }

    @Test
    void getOverdue_shouldReturnList() {
        LocalDateTime before = LocalDateTime.now();
        when(securityUtils.getCurrentUserId()).thenReturn(USER_UUID);
        when(applicationRepository.findOverdueByUserId(eq(USER_UUID), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<ApplicationResponse> result = applicationService.getOverdue();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(applicationRepository).findOverdueByUserId(eq(USER_UUID), thresholdCaptor.capture());

        LocalDateTime expectedMin = before.minusHours(6).minusSeconds(1);
        LocalDateTime expectedMax = after.minusHours(6).plusSeconds(1);
        assertThat(thresholdCaptor.getValue()).isBetween(expectedMin, expectedMax);
        assertThat(result).isEmpty();
    }

    private User buildUser(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setName("Test User");
        u.setEmail(email);
        u.setPasswordHash("hashed");
        return u;
    }

    private JobApplication buildApplication(UUID id, User user) {
        JobApplication a = new JobApplication();
        a.setId(id);
        a.setVacancyName("Software Engineer");
        a.setVacancyOpenedBy("HR");
        a.setStatus(ApplicationStatus.RH);
        a.setApplicationDate(LocalDate.now());
        a.setUser(user);
        return a;
    }

    private ApplicationRequest buildRequest() {
        return new ApplicationRequest(
                "Software Engineer", "Recruiter", "HR",
                "https://example.com/job", LocalDate.now(),
                false, false, null, "RH", false
        );
    }

    private ApplicationResponse buildApplicationResponse(UUID id) {
        return new ApplicationResponse(id, "Software Engineer", "Recruiter", "HR",
                "https://example.com/job", LocalDate.now(), false, false, null, "RH",
                false, LocalDateTime.now(), LocalDateTime.now());
    }
}
