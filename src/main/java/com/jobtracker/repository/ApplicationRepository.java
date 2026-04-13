package com.jobtracker.repository;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<JobApplication, Long>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndInterviewScheduledTrue(Long userId);

    long countByUserIdAndRecruiterDmReminderEnabledTrue(Long userId);

    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.user.id = :userId AND a.status IN :statuses")
    long countByUserIdAndStatusIn(@Param("userId") Long userId, @Param("statuses") List<ApplicationStatus> statuses);

    @Query("SELECT a FROM JobApplication a WHERE a.user.id = :userId AND a.nextStepDateTime IS NOT NULL AND a.nextStepDateTime > :now ORDER BY a.nextStepDateTime ASC")
    List<JobApplication> findUpcomingByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT a FROM JobApplication a WHERE a.user.id = :userId AND a.nextStepDateTime IS NOT NULL AND a.nextStepDateTime < :now ORDER BY a.nextStepDateTime DESC")
    List<JobApplication> findOverdueByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
