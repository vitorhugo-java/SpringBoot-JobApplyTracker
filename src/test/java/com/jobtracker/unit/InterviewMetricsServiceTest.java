package com.jobtracker.unit;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.UserInterviewMetrics;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.repository.UserInterviewMetricsRepository;
import com.jobtracker.service.InterviewMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewMetricsServiceTest {

    @Mock
    private UserInterviewMetricsRepository metricsRepository;

    @Mock
    private InterviewEventRepository eventRepository;

    private InterviewMetricsService service;
    private User user;
    private JobApplication application;
    private UserInterviewMetrics metrics;

    @BeforeEach
    void setUp() {
        service = new InterviewMetricsService(metricsRepository, eventRepository);

        user = new User();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        application = new JobApplication();
        application.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        application.setUser(user);

        metrics = new UserInterviewMetrics();
        metrics.setUser(user);
        metrics.setInterviewCount(2);
    }

    @Test
    void isInterviewStatus_shouldDetectDisplayNames() {
        assertThat(service.isInterviewStatus("Entrevista marcada")).isTrue();
        assertThat(service.isInterviewStatus("Fiz a RH - Aguardando Atualização")).isTrue();
        assertThat(service.isInterviewStatus("Teste Técnico")).isTrue();
        assertThat(service.isInterviewStatus("RH")).isFalse();
        assertThat(service.isInterviewStatus("Unknown")).isFalse();
    }

    @Test
    void wasInterviewTriggered_shouldOnlyDetectEntryIntoInterviewStatus() {
        assertThat(service.wasInterviewTriggered(ApplicationStatus.RH, ApplicationStatus.TESTE_TECNICO)).isTrue();
        assertThat(service.wasInterviewTriggered(ApplicationStatus.TESTE_TECNICO, ApplicationStatus.TESTE_TECNICO)).isFalse();
        assertThat(service.wasInterviewTriggered(ApplicationStatus.TESTE_TECNICO, ApplicationStatus.RH_NEGOCIACAO)).isFalse();
        assertThat(service.wasInterviewTriggered(ApplicationStatus.TESTE_TECNICO, ApplicationStatus.REJEITADO)).isFalse();
        assertThat(service.wasInterviewTriggered(ApplicationStatus.REJEITADO, ApplicationStatus.RH_NEGOCIACAO)).isTrue();
    }

    @Test
    void recordStatusTransition_shouldIncrementAndLogWhenEnteringInterviewStatus() {
        when(metricsRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(metrics));

        service.recordStatusTransition(application, ApplicationStatus.RH, ApplicationStatus.TESTE_TECNICO);

        assertThat(metrics.getInterviewCount()).isEqualTo(3);
        verify(metricsRepository).save(metrics);
        verify(eventRepository).save(any());
    }

    @Test
    void recordStatusTransition_shouldCreateMetricsWhenMissing() {
        when(metricsRepository.findByUser_Id(user.getId())).thenReturn(Optional.empty());
        when(metricsRepository.save(any(UserInterviewMetrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordStatusTransition(application, ApplicationStatus.RH, ApplicationStatus.TESTE_TECNICO);

        ArgumentCaptor<UserInterviewMetrics> metricsCaptor = ArgumentCaptor.forClass(UserInterviewMetrics.class);
        verify(metricsRepository).save(metricsCaptor.capture());
        assertThat(metricsCaptor.getAllValues().getLast().getInterviewCount()).isEqualTo(1);
        verify(eventRepository).save(any());
    }

    @Test
    void recordStatusTransition_shouldNotDoubleCountRepeatedSavesOrInterviewFlowMoves() {
        service.recordStatusTransition(application, ApplicationStatus.TESTE_TECNICO, ApplicationStatus.TESTE_TECNICO);
        service.recordStatusTransition(application, ApplicationStatus.TESTE_TECNICO, ApplicationStatus.RH_NEGOCIACAO);

        verify(metricsRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }
}
