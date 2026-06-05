package com.jobtracker.unit;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.InterviewEventRepository;
import com.jobtracker.service.InterviewMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterviewMetricsServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private InterviewEventRepository eventRepository;

    private InterviewMetricsService service;
    private User user;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        service = new InterviewMetricsService(applicationRepository, eventRepository);

        user = new User();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        application = new JobApplication();
        application.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        application.setUser(user);
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
    void recordStatusTransition_shouldIncrementCountAndLogEventWhenEnteringInterviewStatus() {
        service.recordStatusTransition(application, ApplicationStatus.RH, ApplicationStatus.TESTE_TECNICO);

        assertThat(application.getInterviewCount()).isEqualTo(1);
        verify(eventRepository).save(any());
    }

    @Test
    void recordStatusTransition_shouldNotIncrementWhenStayingWithinInterviewStatuses() {
        service.recordStatusTransition(application, ApplicationStatus.TESTE_TECNICO, ApplicationStatus.RH_NEGOCIACAO);

        assertThat(application.getInterviewCount()).isEqualTo(0);
        verify(eventRepository, never()).save(any());
    }

    @Test
    void recordStatusTransition_shouldNotIncrementForNonInterviewTransitions() {
        service.recordStatusTransition(application, ApplicationStatus.TESTE_TECNICO, ApplicationStatus.TESTE_TECNICO);
        service.recordStatusTransition(application, ApplicationStatus.RH, ApplicationStatus.REJEITADO);

        assertThat(application.getInterviewCount()).isEqualTo(0);
        verify(eventRepository, never()).save(any());
    }
}
