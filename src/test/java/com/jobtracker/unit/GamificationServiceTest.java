package com.jobtracker.unit;

import com.jobtracker.dto.gamification.GamificationEventRequest;
import com.jobtracker.dto.gamification.GamificationEventResponse;
import com.jobtracker.dto.gamification.GamificationProfileResponse;
import com.jobtracker.entity.Achievement;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.User;
import com.jobtracker.entity.UserAchievement;
import com.jobtracker.entity.UserGamification;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.entity.enums.GamificationEventType;
import com.jobtracker.repository.AchievementRepository;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.service.GamificationService;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private UserGamificationRepository userGamificationRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private GamificationService gamificationService;

    private User user;
    private UserGamification state;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        user.setName("Test User");
        user.setEmail("test@example.com");

        state = new UserGamification();
        state.setUser(user);
        state.setCurrentXp(90);
        state.setLevel(1);
        state.setStreakDays(0);

        lenient().when(userGamificationRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(state));
        lenient().when(userGamificationRepository.save(any(UserGamification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId())).thenReturn(List.of());
        lenient().when(userAchievementRepository.findAllByUser_IdOrderByAchievedAtDesc(user.getId())).thenReturn(List.of());
        lenient().when(achievementRepository.findByCode(any())).thenAnswer(invocation -> Optional.of(achievement(invocation.getArgument(0))));
        lenient().when(achievementRepository.save(any(Achievement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getProfile_shouldReturnDerivedLevelProgress() {
        when(securityUtils.getCurrentUser()).thenReturn(user);

        GamificationProfileResponse response = gamificationService.getProfile();

        assertThat(response.currentXp()).isEqualTo(90);
        assertThat(response.level()).isEqualTo(1);
        assertThat(response.nextLevelXp()).isEqualTo(100);
        assertThat(response.progressPercentage()).isEqualTo(90);
        assertThat(response.rankTitle()).isEqualTo("Desempregado de Aluguel");
    }

    @Test
    void applyEvent_shouldAddXpAndDetectLevelUp() {
        when(securityUtils.getCurrentUser()).thenReturn(user);

        GamificationEventRequest request = new GamificationEventRequest(
                GamificationEventType.APPLICATION_CREATED,
                null,
                LocalDateTime.now()
        );

        GamificationEventResponse response = gamificationService.applyEvent(request);

        assertThat(response.xpAwarded()).isEqualTo(10);
        assertThat(response.leveledUp()).isTrue();
        assertThat(response.profile().currentXp()).isEqualTo(100);
        assertThat(response.profile().level()).isEqualTo(2);
    }

    @Test
    void onApplicationUpdated_shouldAwardInterviewOnlyOnce() {
        JobApplication application = application(ApplicationStatus.TESTE_TECNICO, null);
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gamificationService.onApplicationStatusUpdated(application, ApplicationStatus.RH);
        gamificationService.onApplicationStatusUpdated(application, ApplicationStatus.TESTE_TECNICO);

        assertThat(state.getCurrentXp()).isEqualTo(140);
        assertThat(application.isInterviewProgressXpAwarded()).isTrue();
        verify(applicationRepository, times(1)).save(eq(application));
    }

    @Test
    void onApplicationUpdated_shouldAwardNoteOnlyWhenTransitioningFromBlank() {
        JobApplication application = application(ApplicationStatus.RH, "Fresh note");
        when(applicationRepository.save(any(JobApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gamificationService.onApplicationUpdated(application, ApplicationStatus.RH, false, null);
        gamificationService.onApplicationUpdated(application, ApplicationStatus.RH, false, "Fresh note");

        assertThat(state.getCurrentXp()).isEqualTo(95);
        assertThat(application.isNoteAddedXpAwarded()).isTrue();
    }

    @Test
    void getAchievements_shouldUnlockInitialFeasibleAchievements() {
        List<UserAchievement> unlocked = new ArrayList<>();
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId())).thenReturn(List.of(
                applicationAt(1, LocalDate.now().minusDays(4), LocalDateTime.now().withHour(8), ApplicationStatus.RH, null),
                applicationAt(2, LocalDate.now().minusDays(3), LocalDateTime.now().withHour(8), ApplicationStatus.RH, null),
                applicationAt(3, LocalDate.now().minusDays(2), LocalDateTime.now().withHour(8), ApplicationStatus.RH, null),
                applicationAt(4, LocalDate.now().minusDays(1), LocalDateTime.now().withHour(8), ApplicationStatus.RH, null),
                applicationAt(5, LocalDate.now(), LocalDateTime.now().withHour(8), ApplicationStatus.GHOSTING, LocalDateTime.now().minusDays(1)),
                applicationAt(6, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(6)),
                applicationAt(7, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(6).plusHours(1)),
                applicationAt(8, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(5)),
                applicationAt(9, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(5).plusHours(1)),
                applicationAt(10, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(4)),
                applicationAt(11, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(4).plusHours(1)),
                applicationAt(12, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(3)),
                applicationAt(13, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(3).plusHours(1)),
                applicationAt(14, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(2)),
                applicationAt(15, LocalDate.now(), LocalDateTime.now().withHour(10), ApplicationStatus.RH, LocalDateTime.now().minusDays(2).plusHours(1))
        ));
        when(userAchievementRepository.findAllByUser_IdOrderByAchievedAtDesc(user.getId())).thenAnswer(invocation -> unlocked);
        when(userAchievementRepository.save(any(UserAchievement.class))).thenAnswer(invocation -> {
            UserAchievement achievement = invocation.getArgument(0);
            unlocked.add(achievement);
            return achievement;
        });

        assertThat(gamificationService.getAchievements())
                .hasSize(4)
                .allMatch(achievement -> achievement.unlocked());
    }

    private JobApplication application(ApplicationStatus status, String note) {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setUser(user);
        application.setApplicationDate(LocalDate.now());
        application.setStatus(status);
        application.setNote(note);
        return application;
    }

    private JobApplication applicationAt(int suffix,
                                         LocalDate applicationDate,
                                         LocalDateTime createdAt,
                                         ApplicationStatus status,
                                         LocalDateTime dmSentAt) {
        JobApplication application = new JobApplication();
        application.setId(UUID.fromString("00000000-0000-0000-0000-0000000000" + String.format("%02d", suffix)));
        application.setUser(user);
        application.setApplicationDate(applicationDate);
        application.setCreatedAt(createdAt);
        application.setStatus(status);
        application.setRecruiterDmSentAt(dmSentAt);
        return application;
    }

    private Achievement achievement(String code) {
        Achievement achievement = new Achievement();
        achievement.setId(UUID.randomUUID());
        achievement.setCode(code);
        achievement.setName(code);
        achievement.setDescription(code);
        return achievement;
    }
}
