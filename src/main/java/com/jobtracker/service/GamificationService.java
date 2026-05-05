package com.jobtracker.service;

import com.jobtracker.dto.gamification.AchievementResponse;
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
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.repository.AchievementRepository;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.repository.UserAchievementRepository;
import com.jobtracker.repository.UserGamificationRepository;
import com.jobtracker.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class GamificationService {

    private static final LocalTime EARLY_BIRD_CUTOFF = LocalTime.of(9, 0);

    /*
     * Backend funnel mapping:
     * - INTERVIEW_PROGRESS starts once the application reaches a post-screening step
     *   (RH done, hiring manager done, technical step, or negotiation) or when
     *   interviewScheduled becomes true.
     * - OFFER_WON maps to RH_NEGOCIACAO, which is the closest current backend status
     *   to an offer/closing stage until dedicated OFFER/HIRED statuses exist.
     */
    private static final Set<ApplicationStatus> INTERVIEW_PROGRESS_STATUSES = EnumSet.of(
            ApplicationStatus.FIZ_A_RH_AGUARDANDO_ATUALIZACAO,
            ApplicationStatus.FIZ_A_HIRING_MANAGER_AGUARDANDO_ATUALIZACAO,
            ApplicationStatus.TESTE_TECNICO,
            ApplicationStatus.FIZ_TESTE_TECNICO_AGUARDANDO_ATUALIZACAO,
            ApplicationStatus.RH_NEGOCIACAO
    );

    private static final String EARLY_BIRD = "EARLY_BIRD";
    private static final String NETWORKING_PRO = "NETWORKING_PRO";
    private static final String PERSISTENT = "PERSISTENT";
    private static final String GHOSTBUSTER = "GHOSTBUSTER";

    private static final List<AchievementDefinition> ACHIEVEMENT_CATALOG = List.of(
            new AchievementDefinition(EARLY_BIRD, "Early Bird", "Aplicou para 5 vagas antes das 09:00.", "sunrise"),
            new AchievementDefinition(NETWORKING_PRO, "Networking Pro", "Mandou 10 DMs para recrutadores em uma semana.", "messages-square"),
            new AchievementDefinition(PERSISTENT, "Persistent", "Aplicou por 5 dias seguidos.", "flame"),
            new AchievementDefinition(GHOSTBUSTER, "Ghostbuster", "Identificou uma vaga sem resposta há 30 dias.", "ghost")
    );

    private final UserGamificationRepository userGamificationRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRepository achievementRepository;
    private final ApplicationRepository applicationRepository;
    private final SecurityUtils securityUtils;

    public GamificationService(UserGamificationRepository userGamificationRepository,
                               UserAchievementRepository userAchievementRepository,
                               AchievementRepository achievementRepository,
                               ApplicationRepository applicationRepository,
                               SecurityUtils securityUtils) {
        this.userGamificationRepository = userGamificationRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.achievementRepository = achievementRepository;
        this.applicationRepository = applicationRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public GamificationProfileResponse getProfile() {
        User user = securityUtils.getCurrentUser();
        UserGamification state = findOrCreateState(user);
        List<JobApplication> applications = applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId());
        syncDerivedState(user, state, applications);
        return toProfileResponse(state);
    }

    @Transactional
    public List<AchievementResponse> getAchievements() {
        User user = securityUtils.getCurrentUser();
        UserGamification state = findOrCreateState(user);
        List<JobApplication> applications = applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId());
        syncDerivedState(user, state, applications);

        Map<String, UserAchievement> unlockedByCode = userAchievementRepository.findAllByUser_IdOrderByAchievedAtDesc(user.getId())
                .stream()
                .filter(entry -> entry.getAchievement() != null)
                .collect(Collectors.toMap(
                        entry -> entry.getAchievement().getCode(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return ACHIEVEMENT_CATALOG.stream()
                .map(achievement -> {
                    UserAchievement unlocked = unlockedByCode.get(achievement.code());
                    return new AchievementResponse(
                            achievement.code(),
                            achievement.name(),
                            achievement.description(),
                            achievement.icon(),
                            unlocked != null,
                            unlocked != null ? unlocked.getAchievedAt() : null
                    );
                })
                .toList();
    }

    @Transactional
    public GamificationEventResponse applyEvent(GamificationEventRequest request) {
        User user = securityUtils.getCurrentUser();
        LocalDateTime occurredAt = request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now();

        EventResult result = request.applicationId() != null
                ? applyApplicationEvent(user, request.applicationId(), request.eventType(), occurredAt)
                : grantXp(user, request.eventType(), occurredAt);

        return toEventResponse(request.eventType(), result);
    }

    @Transactional
    public void onApplicationCreated(JobApplication application) {
        awardApplicationCreated(application, application.getCreatedAt() != null ? application.getCreatedAt() : LocalDateTime.now());
        if (hasNote(application)) {
            awardNoteAdded(application, LocalDateTime.now());
        }
        if (qualifiesForInterviewProgress(application)) {
            awardInterviewProgress(application, LocalDateTime.now());
        }
        if (qualifiesForOfferWon(application)) {
            awardOfferWon(application, LocalDateTime.now());
        }
    }

    @Transactional
    public void onApplicationUpdated(JobApplication application,
                                     ApplicationStatus previousStatus,
                                     boolean previousInterviewScheduled,
                                     String previousNote) {
        if (!StringUtils.hasText(previousNote) && hasNote(application)) {
            awardNoteAdded(application, LocalDateTime.now());
        }
        if (!previousInterviewScheduled && application.isInterviewScheduled()) {
            awardInterviewProgress(application, LocalDateTime.now());
        }
        if (statusEntered(previousStatus, application.getStatus(), INTERVIEW_PROGRESS_STATUSES)) {
            awardInterviewProgress(application, LocalDateTime.now());
        }
        if (application.getStatus() != previousStatus && qualifiesForOfferWon(application)) {
            awardOfferWon(application, LocalDateTime.now());
        }
    }

    @Transactional
    public void onApplicationStatusUpdated(JobApplication application, ApplicationStatus previousStatus) {
        if (statusEntered(previousStatus, application.getStatus(), INTERVIEW_PROGRESS_STATUSES)) {
            awardInterviewProgress(application, LocalDateTime.now());
        }
        if (application.getStatus() != previousStatus && qualifiesForOfferWon(application)) {
            awardOfferWon(application, LocalDateTime.now());
        }
    }

    @Transactional
    public void onRecruiterDmSent(JobApplication application, boolean firstTimeDmSent) {
        if (!firstTimeDmSent) {
            syncCurrentState(application.getUser());
            return;
        }
        awardRecruiterDmSent(application, application.getRecruiterDmSentAt() != null ? application.getRecruiterDmSentAt() : LocalDateTime.now());
    }

    private EventResult applyApplicationEvent(User user, UUID applicationId, GamificationEventType eventType, LocalDateTime occurredAt) {
        JobApplication application = applicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));

        return switch (eventType) {
            case APPLICATION_CREATED -> awardWithFlag(application, eventType, occurredAt,
                    application::isApplicationCreatedXpAwarded,
                    application::setApplicationCreatedXpAwarded);
            case RECRUITER_DM_SENT -> application.getRecruiterDmSentAt() == null
                    ? noOp(user, eventType, "DM ainda não foi marcada como enviada para esta candidatura.")
                    : awardWithFlag(application, eventType, occurredAt,
                    application::isRecruiterDmXpAwarded,
                    application::setRecruiterDmXpAwarded);
            case INTERVIEW_PROGRESS -> !qualifiesForInterviewProgress(application)
                    ? noOp(user, eventType, "A candidatura ainda não chegou a uma etapa equivalente de entrevista.")
                    : awardWithFlag(application, eventType, occurredAt,
                    application::isInterviewProgressXpAwarded,
                    application::setInterviewProgressXpAwarded);
            case NOTE_ADDED -> !hasNote(application)
                    ? noOp(user, eventType, "Adicione uma nota real antes de contabilizar XP.")
                    : awardWithFlag(application, eventType, occurredAt,
                    application::isNoteAddedXpAwarded,
                    application::setNoteAddedXpAwarded);
            case OFFER_WON -> !qualifiesForOfferWon(application)
                    ? noOp(user, eventType, "A candidatura ainda não chegou ao estágio mapeado como oferta.")
                    : awardWithFlag(application, eventType, occurredAt,
                    application::isOfferWonXpAwarded,
                    application::setOfferWonXpAwarded);
        };
    }

    private void awardApplicationCreated(JobApplication application, LocalDateTime occurredAt) {
        awardWithFlag(application, GamificationEventType.APPLICATION_CREATED, occurredAt,
                application::isApplicationCreatedXpAwarded,
                application::setApplicationCreatedXpAwarded);
    }

    private void awardRecruiterDmSent(JobApplication application, LocalDateTime occurredAt) {
        awardWithFlag(application, GamificationEventType.RECRUITER_DM_SENT, occurredAt,
                application::isRecruiterDmXpAwarded,
                application::setRecruiterDmXpAwarded);
    }

    private void awardInterviewProgress(JobApplication application, LocalDateTime occurredAt) {
        awardWithFlag(application, GamificationEventType.INTERVIEW_PROGRESS, occurredAt,
                application::isInterviewProgressXpAwarded,
                application::setInterviewProgressXpAwarded);
    }

    private void awardNoteAdded(JobApplication application, LocalDateTime occurredAt) {
        awardWithFlag(application, GamificationEventType.NOTE_ADDED, occurredAt,
                application::isNoteAddedXpAwarded,
                application::setNoteAddedXpAwarded);
    }

    private void awardOfferWon(JobApplication application, LocalDateTime occurredAt) {
        awardWithFlag(application, GamificationEventType.OFFER_WON, occurredAt,
                application::isOfferWonXpAwarded,
                application::setOfferWonXpAwarded);
    }

    private EventResult awardWithFlag(JobApplication application,
                                      GamificationEventType eventType,
                                      LocalDateTime occurredAt,
                                      Supplier<Boolean> alreadyAwarded,
                                      Consumer<Boolean> markAwarded) {
        if (alreadyAwarded.get()) {
            return noOp(application.getUser(), eventType, "Evento já contabilizado para esta candidatura.");
        }

        markAwarded.accept(true);
        applicationRepository.save(application);
        return grantXp(application.getUser(), eventType, occurredAt);
    }

    private EventResult grantXp(User user, GamificationEventType eventType, LocalDateTime occurredAt) {
        UserGamification state = findOrCreateState(user);
        int previousLevel = state.getLevel();
        int xpAwarded = xpForEvent(eventType);

        state.setCurrentXp(state.getCurrentXp() + xpAwarded);
        state.setLevel(calculateLevel(state.getCurrentXp()));
        updateLastActivity(state, occurredAt);
        userGamificationRepository.save(state);

        List<JobApplication> applications = applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId());
        syncDerivedState(user, state, applications);

        return new EventResult(
                xpAwarded,
                state.getLevel() > previousLevel,
                buildAwardMessage(eventType, xpAwarded, state.getLevel() > previousLevel),
                toProfileResponse(state)
        );
    }

    private EventResult noOp(User user, GamificationEventType eventType, String message) {
        UserGamification state = findOrCreateState(user);
        List<JobApplication> applications = applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId());
        syncDerivedState(user, state, applications);
        return new EventResult(0, false, message, toProfileResponse(state));
    }

    private void syncCurrentState(User user) {
        UserGamification state = findOrCreateState(user);
        List<JobApplication> applications = applicationRepository.findAllByUser_IdAndArchivedFalse(user.getId());
        syncDerivedState(user, state, applications);
    }

    private void syncDerivedState(User user, UserGamification state, List<JobApplication> applications) {
        int recalculatedLevel = calculateLevel(state.getCurrentXp());
        int currentStreak = calculateCurrentStreak(applications);
        boolean dirty = false;

        if (state.getLevel() != recalculatedLevel) {
            state.setLevel(recalculatedLevel);
            dirty = true;
        }
        if (state.getStreakDays() != currentStreak) {
            state.setStreakDays(currentStreak);
            dirty = true;
        }

        if (dirty) {
            userGamificationRepository.save(state);
        }

        unlockAchievements(user, applications);
    }

    private void unlockAchievements(User user, List<JobApplication> applications) {
        Map<String, Achievement> achievementsByCode = ensureAchievementCatalog();
        Set<String> unlockedCodes = userAchievementRepository.findAllByUser_IdOrderByAchievedAtDesc(user.getId())
                .stream()
                .filter(entry -> entry.getAchievement() != null)
                .map(entry -> entry.getAchievement().getCode())
                .collect(Collectors.toSet());

        unlockIfEligible(user, unlockedCodes, achievementsByCode, EARLY_BIRD, hasEarlyBirdSignal(applications));
        unlockIfEligible(user, unlockedCodes, achievementsByCode, NETWORKING_PRO, hasNetworkingProSignal(applications));
        unlockIfEligible(user, unlockedCodes, achievementsByCode, PERSISTENT, hasPersistentSignal(applications));
        unlockIfEligible(user, unlockedCodes, achievementsByCode, GHOSTBUSTER, hasGhostbusterSignal(applications));
    }

    private void unlockIfEligible(User user,
                                  Set<String> unlockedCodes,
                                  Map<String, Achievement> achievementsByCode,
                                  String code,
                                  boolean eligible) {
        if (!eligible || unlockedCodes.contains(code)) {
            return;
        }

        UserAchievement userAchievement = new UserAchievement();
        userAchievement.setUser(user);
        userAchievement.setAchievement(achievementsByCode.get(code));
        userAchievement.setAchievedAt(LocalDateTime.now());
        userAchievementRepository.save(userAchievement);
        unlockedCodes.add(code);
    }

    private Map<String, Achievement> ensureAchievementCatalog() {
        Map<String, Achievement> achievementsByCode = new LinkedHashMap<>();
        for (AchievementDefinition definition : ACHIEVEMENT_CATALOG) {
            Achievement achievement = achievementRepository.findByCode(definition.code())
                    .orElseGet(() -> achievementRepository.save(definition.toEntity()));
            achievementsByCode.put(definition.code(), achievement);
        }
        return achievementsByCode;
    }

    private boolean hasEarlyBirdSignal(List<JobApplication> applications) {
        return applications.stream()
                .filter(application -> application.getApplicationDate() != null)
                .filter(application -> application.getCreatedAt() != null)
                .filter(application -> application.getCreatedAt().toLocalTime().isBefore(EARLY_BIRD_CUTOFF))
                .count() >= 5;
    }

    private boolean hasNetworkingProSignal(List<JobApplication> applications) {
        List<LocalDateTime> dmDates = applications.stream()
                .map(JobApplication::getRecruiterDmSentAt)
                .filter(date -> date != null)
                .sorted()
                .toList();

        int left = 0;
        for (int right = 0; right < dmDates.size(); right++) {
            while (ChronoUnit.DAYS.between(dmDates.get(left), dmDates.get(right)) > 7) {
                left++;
            }
            if ((right - left + 1) >= 10) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPersistentSignal(List<JobApplication> applications) {
        return calculateLongestStreak(applications) >= 5;
    }

    private boolean hasGhostbusterSignal(List<JobApplication> applications) {
        return applications.stream().anyMatch(application -> application.getStatus() == ApplicationStatus.GHOSTING);
    }

    private int calculateCurrentStreak(List<JobApplication> applications) {
        List<LocalDate> distinctDates = distinctApplicationDates(applications);
        if (distinctDates.isEmpty()) {
            return 0;
        }

        int streak = 1;
        for (int index = 1; index < distinctDates.size(); index++) {
            LocalDate previous = distinctDates.get(index - 1);
            LocalDate current = distinctDates.get(index);
            if (ChronoUnit.DAYS.between(current, previous) == 1) {
                streak++;
                continue;
            }
            break;
        }
        return streak;
    }

    private int calculateLongestStreak(List<JobApplication> applications) {
        List<LocalDate> distinctDates = distinctApplicationDates(applications);
        if (distinctDates.isEmpty()) {
            return 0;
        }

        int longest = 1;
        int current = 1;
        for (int index = 1; index < distinctDates.size(); index++) {
            LocalDate previous = distinctDates.get(index - 1);
            LocalDate candidate = distinctDates.get(index);
            if (ChronoUnit.DAYS.between(candidate, previous) == 1) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }

    private List<LocalDate> distinctApplicationDates(List<JobApplication> applications) {
        return applications.stream()
                .map(JobApplication::getApplicationDate)
                .filter(date -> date != null)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private boolean hasNote(JobApplication application) {
        return StringUtils.hasText(application.getNote());
    }

    private boolean qualifiesForInterviewProgress(JobApplication application) {
        return application.isInterviewScheduled() || INTERVIEW_PROGRESS_STATUSES.contains(application.getStatus());
    }

    private boolean qualifiesForOfferWon(JobApplication application) {
        return application.getStatus() == ApplicationStatus.RH_NEGOCIACAO;
    }

    private boolean statusEntered(ApplicationStatus previousStatus,
                                  ApplicationStatus currentStatus,
                                  Set<ApplicationStatus> qualifyingStatuses) {
        return currentStatus != null
                && qualifyingStatuses.contains(currentStatus)
                && currentStatus != previousStatus;
    }

    private void updateLastActivity(UserGamification state, LocalDateTime occurredAt) {
        if (occurredAt == null) {
            return;
        }
        if (state.getLastActivityAt() == null || occurredAt.isAfter(state.getLastActivityAt())) {
            state.setLastActivityAt(occurredAt);
        }
    }

    private UserGamification findOrCreateState(User user) {
        return userGamificationRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    UserGamification created = new UserGamification();
                    created.setUser(user);
                    created.setCurrentXp(0);
                    created.setLevel(1);
                    created.setStreakDays(0);
                    return userGamificationRepository.save(created);
                });
    }

    private GamificationEventResponse toEventResponse(GamificationEventType eventType, EventResult result) {
        return new GamificationEventResponse(
                eventType,
                result.xpAwarded(),
                result.leveledUp(),
                result.message(),
                result.profile()
        );
    }

    private GamificationProfileResponse toProfileResponse(UserGamification state) {
        long currentLevelXp = xpForLevel(state.getLevel());
        long nextLevelXp = xpForLevel(state.getLevel() + 1);
        long xpToNextLevel = Math.max(0, nextLevelXp - state.getCurrentXp());
        long levelSpan = Math.max(1, nextLevelXp - currentLevelXp);
        int progressPercentage = (int) Math.min(100,
                Math.max(0, ((state.getCurrentXp() - currentLevelXp) * 100) / levelSpan));

        return new GamificationProfileResponse(
                state.getCurrentXp(),
                state.getLevel(),
                currentLevelXp,
                nextLevelXp,
                xpToNextLevel,
                progressPercentage,
                resolveRankTitle(state.getLevel()),
                state.getStreakDays()
        );
    }

    int calculateLevel(long totalXp) {
        return (int) Math.floor(Math.sqrt(totalXp / 100.0d)) + 1;
    }

    long xpForLevel(int level) {
        return 100L * (long) Math.pow(Math.max(0, level - 1), 2);
    }

    String resolveRankTitle(int level) {
        if (level >= 51) {
            return "Lenda das Contratacoes";
        }
        if (level >= 31) {
            return "Mestre das Soft Skills";
        }
        if (level >= 16) {
            return "Sobrevivente do LinkedIn";
        }
        if (level >= 6) {
            return "Job Hunter Iniciante";
        }
        return "Desempregado de Aluguel";
    }

    private int xpForEvent(GamificationEventType eventType) {
        return switch (eventType) {
            case APPLICATION_CREATED -> 10;
            case RECRUITER_DM_SENT -> 15;
            case INTERVIEW_PROGRESS -> 50;
            case NOTE_ADDED -> 5;
            case OFFER_WON -> 500;
        };
    }

    private String buildAwardMessage(GamificationEventType eventType, int xpAwarded, boolean leveledUp) {
        String baseMessage = switch (eventType) {
            case APPLICATION_CREATED -> "+%d XP por registrar uma nova aplicacao".formatted(xpAwarded);
            case RECRUITER_DM_SENT -> "+%d XP por fortalecer o networking".formatted(xpAwarded);
            case INTERVIEW_PROGRESS -> "+%d XP por avancar no funil".formatted(xpAwarded);
            case NOTE_ADDED -> "+%d XP por registrar aprendizado".formatted(xpAwarded);
            case OFFER_WON -> "+%d XP por chegar ao estagio de oferta/negociacao".formatted(xpAwarded);
        };
        return leveledUp ? baseMessage + " e subir de nivel." : baseMessage + ".";
    }

    private record AchievementDefinition(String code, String name, String description, String icon) {
        private Achievement toEntity() {
            Achievement achievement = new Achievement();
            achievement.setCode(code);
            achievement.setName(name);
            achievement.setDescription(description);
            achievement.setIcon(icon);
            return achievement;
        }
    }

    private record EventResult(int xpAwarded, boolean leveledUp, String message, GamificationProfileResponse profile) {
    }
}
