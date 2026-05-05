package com.jobtracker.repository;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<JobApplication, UUID>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM JobApplication a WHERE a.id = :id AND a.user.id = :userId")
    Optional<JobApplication> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    long countByUserIdAndArchivedFalse(UUID userId);

    long countByUserIdAndInterviewScheduledTrueAndArchivedFalse(UUID userId);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.status = :status AND a.archived = false")
    long countByUserIdAndStatusAndArchivedFalse(@Param("userId") UUID userId, @Param("status") ApplicationStatus status);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.status IS NOT NULL AND a.recruiterDmReminderEnabled = true AND a.recruiterDmSentAt IS NULL AND a.archived = false")
    long countPendingDmRemindersByUserId(@Param("userId") UUID userId);

    long countByUserIdAndStatusIsNullAndArchivedFalse(UUID userId);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.applicationDate >= :fromDate AND a.archived = false")
    long countByUserIdAndApplicationDateSince(@Param("userId") UUID userId, @Param("fromDate") java.time.LocalDate fromDate);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.applicationDate = :date AND a.archived = false")
    long countByUserIdAndApplicationDateAndArchivedFalse(@Param("userId") UUID userId, @Param("date") java.time.LocalDate date);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.status IN :statuses AND a.archived = false")
    long countByUserIdAndStatusInAndArchivedFalse(@Param("userId") UUID userId, @Param("statuses") List<ApplicationStatus> statuses);

    List<JobApplication> findAllByUser_IdAndArchivedFalse(UUID userId);

    @Query("SELECT a FROM JobApplication a WHERE a.user.id = :userId AND a.status IS NOT NULL AND a.recruiterDmReminderEnabled = true AND a.recruiterDmSentAt IS NULL AND a.createdAt > :reminderThreshold AND a.archived = false ORDER BY a.createdAt ASC")
    List<JobApplication> findUpcomingByUserId(@Param("userId") UUID userId, @Param("reminderThreshold") LocalDateTime reminderThreshold);

    @Query("SELECT a FROM JobApplication a WHERE a.user.id = :userId AND a.status IS NOT NULL AND a.recruiterDmReminderEnabled = true AND a.recruiterDmSentAt IS NULL AND a.createdAt <= :reminderThreshold AND a.createdAt > :expireThreshold AND a.archived = false ORDER BY a.createdAt ASC")
    List<JobApplication> findOverdueByUserId(@Param("userId") UUID userId, @Param("reminderThreshold") LocalDateTime reminderThreshold, @Param("expireThreshold") LocalDateTime expireThreshold);

    List<JobApplication> findByStatusIsNullAndUpdatedAtBefore(LocalDateTime updatedAt);

    List<JobApplication> findByStatusIsNotNullAndStatusNotAndUpdatedAtBefore(ApplicationStatus status, LocalDateTime updatedAt);
}
