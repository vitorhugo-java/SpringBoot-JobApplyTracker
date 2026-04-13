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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private SecurityUtils securityUtils;

    @InjectMocks
    private ApplicationService applicationService;

    private User user;
    private JobApplication app;
    private ApplicationResponse response;

    @BeforeEach
    void setUp() {
        user = buildUser(1L, "user@example.com");
        app = buildApplication(1L, user);
        response = buildApplicationResponse(1L);
    }

    @Test
    void create_shouldSaveAndReturnResponse() {
        ApplicationRequest request = buildRequest();
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(applicationRepository.save(any(JobApplication.class))).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.create(request);
        assertThat(result.id()).isEqualTo(1L);
        verify(applicationRepository).save(any(JobApplication.class));
    }

    @Test
    void getById_shouldReturnApplication_whenFoundAndBelongsToUser() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.getById(1L);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_shouldUpdateAndReturnResponse() {
        ApplicationRequest request = buildRequest();
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.update(1L, request);
        assertThat(result).isNotNull();
    }

    @Test
    void updateStatus_shouldUpdateStatus() {
        UpdateStatusRequest statusRequest = new UpdateStatusRequest("RH");
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationResponse result = applicationService.updateStatus(1L, statusRequest);
        assertThat(result).isNotNull();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.RH);
    }

    @Test
    void updateStatus_shouldThrow_whenInvalidStatus() {
        UpdateStatusRequest statusRequest = new UpdateStatusRequest("INVALID_STATUS");
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> applicationService.updateStatus(1L, statusRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid status value");
    }

    @Test
    void updateReminder_shouldToggleReminder() {
        UpdateReminderRequest reminderRequest = new UpdateReminderRequest(true);
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        applicationService.updateReminder(1L, reminderRequest);
        assertThat(app.isRecruiterDmReminderEnabled()).isTrue();
    }

    @Test
    void delete_shouldDeleteApplication() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(app));

        applicationService.delete(1L);
        verify(applicationRepository).delete(app);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> applicationService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void getAll_shouldReturnPagedResponse() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        PageImpl<JobApplication> page = new PageImpl<>(List.of(app));
        when(applicationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(applicationMapper.toResponse(app)).thenReturn(response);

        ApplicationPageResponse result = applicationService.getAll(null, null, null, null, null, null, 0, 10, null);
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getAll_shouldThrow_whenInvalidSortField() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        assertThatThrownBy(() -> applicationService.getAll(null, null, null, null, null, null, 0, 10, "invalidField,asc"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid sort field");
    }

    @Test
    void getUpcoming_shouldReturnList() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findUpcomingByUserId(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(app));
        when(applicationMapper.toResponse(app)).thenReturn(response);

        List<ApplicationResponse> result = applicationService.getUpcoming();
        assertThat(result).hasSize(1);
    }

    @Test
    void getOverdue_shouldReturnList() {
        when(securityUtils.getCurrentUserId()).thenReturn(1L);
        when(applicationRepository.findOverdueByUserId(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<ApplicationResponse> result = applicationService.getOverdue();
        assertThat(result).isEmpty();
    }

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setName("Test User");
        u.setEmail(email);
        u.setPasswordHash("hashed");
        return u;
    }

    private JobApplication buildApplication(Long id, User user) {
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

    private ApplicationResponse buildApplicationResponse(Long id) {
        return new ApplicationResponse(id, "Software Engineer", "Recruiter", "HR",
                "https://example.com/job", LocalDate.now(), false, false, null, "RH",
                false, LocalDateTime.now(), LocalDateTime.now());
    }
}
