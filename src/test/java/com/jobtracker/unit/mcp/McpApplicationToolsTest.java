package com.jobtracker.unit.mcp;

import com.jobtracker.dto.application.ApplicationFilter;
import com.jobtracker.dto.application.ApplicationPageResponse;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.application.ApplicationResponse;
import com.jobtracker.dto.application.MarkDmSentRequest;
import com.jobtracker.dto.application.UpdateReminderRequest;
import com.jobtracker.dto.application.UpdateStatusRequest;
import com.jobtracker.mcp.tools.McpApplicationTools;
import com.jobtracker.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the application MCP tools. Auditing is applied by {@code McpAuditAspect} at the
 * proxy layer and is not exercised here, so the tools are tested as plain delegations to
 * {@link ApplicationService}. The framework-injected {@code McpSyncRequestContext} is passed as
 * {@code null}.
 */
@ExtendWith(MockitoExtension.class)
class McpApplicationToolsTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private McpApplicationTools tools;

    @Test
    void listApplications_allNullParams_usesDefaults() {
        ApplicationPageResponse expected = new ApplicationPageResponse(List.of(), 0, 20, 0, 0);
        when(applicationService.getAll(any(ApplicationFilter.class), eq(0), eq(20), eq("createdAt,desc")))
                .thenReturn(expected);

        ApplicationPageResponse result = tools.listApplications(null, null, null, null, null, null, null, null, null, null);

        assertThat(result).isEqualTo(expected);
        verify(applicationService).getAll(any(ApplicationFilter.class), eq(0), eq(20), eq("createdAt,desc"));
    }

    @Test
    void listApplications_parsesDateStrings() {
        ApplicationPageResponse expected = new ApplicationPageResponse(List.of(), 0, 20, 0, 0);
        when(applicationService.getAll(any(ApplicationFilter.class), eq(0), eq(20), any()))
                .thenReturn(expected);

        tools.listApplications(null, null, null, "2025-01-01", "2025-06-30", null, null, null, null, null);

        ArgumentCaptor<ApplicationFilter> captor = ArgumentCaptor.forClass(ApplicationFilter.class);
        verify(applicationService).getAll(captor.capture(), eq(0), eq(20), eq("createdAt,desc"));
        assertThat(captor.getValue().applicationDateFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(captor.getValue().applicationDateTo()).isEqualTo(LocalDate.of(2025, 6, 30));
    }

    @Test
    void listApplications_honorsExplicitPageAndSort() {
        ApplicationPageResponse expected = new ApplicationPageResponse(List.of(), 2, 5, 0, 0);
        when(applicationService.getAll(any(ApplicationFilter.class), eq(2), eq(5), eq("applicationDate,asc")))
                .thenReturn(expected);

        tools.listApplications(null, null, null, null, null, null, null, 2, 5, "applicationDate,asc");

        verify(applicationService).getAll(any(ApplicationFilter.class), eq(2), eq(5), eq("applicationDate,asc"));
    }

    @Test
    void getApplication_parsesUuid() {
        UUID id = UUID.randomUUID();
        ApplicationResponse expected = applicationResponseWithId(id);
        when(applicationService.getById(id)).thenReturn(expected);

        ApplicationResponse result = tools.getApplication(null, id.toString());

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getUpcomingApplications_delegatesToService() {
        ApplicationResponse resp = applicationResponseWithId(UUID.randomUUID());
        when(applicationService.getUpcoming()).thenReturn(List.of(resp));

        List<ApplicationResponse> result = tools.getUpcomingApplications(null);

        assertThat(result).containsExactly(resp);
    }

    @Test
    void getOverdueApplications_delegatesToService() {
        when(applicationService.getOverdue()).thenReturn(List.of());

        List<ApplicationResponse> result = tools.getOverdueApplications(null);

        assertThat(result).isEmpty();
    }

    @Test
    void createApplication_mapsAllParams() {
        ArgumentCaptor<ApplicationRequest> captor = ArgumentCaptor.forClass(ApplicationRequest.class);
        ApplicationResponse expected = applicationResponseWithId(UUID.randomUUID());
        when(applicationService.create(any())).thenReturn(expected);

        tools.createApplication(
                null,
                "Backend Engineer",
                "Jane Smith",
                "TechCorp",
                "https://example.com/job",
                "2025-06-01",
                Boolean.TRUE,
                Boolean.FALSE,
                "2025-06-10T14:00:00",
                "RH",
                Boolean.TRUE,
                "Follow up Monday",
                "LinkedIn");

        verify(applicationService).create(captor.capture());
        ApplicationRequest req = captor.getValue();
        assertThat(req.vacancyName()).isEqualTo("Backend Engineer");
        assertThat(req.recruiterName()).isEqualTo("Jane Smith");
        assertThat(req.organization()).isEqualTo("TechCorp");
        assertThat(req.applicationDate()).isEqualTo(LocalDate.of(2025, 6, 1));
        assertThat(req.rhAcceptedConnection()).isTrue();
        assertThat(req.interviewScheduled()).isFalse();
        assertThat(req.nextStepDateTime()).isEqualTo(LocalDateTime.of(2025, 6, 10, 14, 0, 0));
        assertThat(req.status()).isEqualTo("RH");
        assertThat(req.recruiterDmReminderEnabled()).isTrue();
        assertThat(req.note()).isEqualTo("Follow up Monday");
        assertThat(req.platform()).isEqualTo("LinkedIn");
    }

    @Test
    void createApplication_nullBooleans_defaultToFalse() {
        ArgumentCaptor<ApplicationRequest> captor = ArgumentCaptor.forClass(ApplicationRequest.class);
        when(applicationService.create(any())).thenReturn(applicationResponseWithId(UUID.randomUUID()));

        tools.createApplication(null, "Vacancy", null, null, null, null, null, null, null, null, null, null, null);

        verify(applicationService).create(captor.capture());
        ApplicationRequest req = captor.getValue();
        assertThat(req.rhAcceptedConnection()).isFalse();
        assertThat(req.interviewScheduled()).isFalse();
        assertThat(req.recruiterDmReminderEnabled()).isFalse();
    }

    @Test
    void updateApplicationStatus_buildsCorrectRequest() {
        UUID id = UUID.randomUUID();
        ApplicationResponse expected = applicationResponseWithId(id);
        ArgumentCaptor<UpdateStatusRequest> captor = ArgumentCaptor.forClass(UpdateStatusRequest.class);
        when(applicationService.updateStatus(eq(id), any())).thenReturn(expected);

        tools.updateApplicationStatus(null, id.toString(), "Teste Técnico");

        verify(applicationService).updateStatus(eq(id), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo("Teste Técnico");
    }

    @Test
    void updateApplicationReminder_passesEnabledFlag() {
        UUID id = UUID.randomUUID();
        ApplicationResponse expected = applicationResponseWithId(id);
        ArgumentCaptor<UpdateReminderRequest> captor = ArgumentCaptor.forClass(UpdateReminderRequest.class);
        when(applicationService.updateReminder(eq(id), any())).thenReturn(expected);

        tools.updateApplicationReminder(null, id.toString(), true);

        verify(applicationService).updateReminder(eq(id), captor.capture());
        assertThat(captor.getValue().recruiterDmReminderEnabled()).isTrue();
    }

    @Test
    void markRecruiterDmSent_delegatesWithEmptyRequest() {
        UUID id = UUID.randomUUID();
        when(applicationService.markDmSent(eq(id), any(MarkDmSentRequest.class)))
                .thenReturn(applicationResponseWithId(id));

        tools.markRecruiterDmSent(null, id.toString());

        verify(applicationService).markDmSent(eq(id), any(MarkDmSentRequest.class));
    }

    @Test
    void archiveApplication_delegatesToService() {
        UUID id = UUID.randomUUID();
        when(applicationService.archive(id)).thenReturn(applicationResponseWithId(id));

        tools.archiveApplication(null, id.toString());

        verify(applicationService).archive(id);
    }

    @Test
    void deleteApplication_delegatesToService() {
        UUID id = UUID.randomUUID();

        tools.deleteApplication(null, id.toString());

        verify(applicationService).delete(id);
    }

    private static ApplicationResponse applicationResponseWithId(UUID id) {
        return new ApplicationResponse(
                id, null, null, null, null,
                null,
                false, false,
                null, null, null,
                false, null, null,
                null,
                false, null,
                null, null, null, null, null,
                false, 0, null, null);
    }
}
