package com.jobtracker.repository;

import com.jobtracker.entity.UserInterviewMetrics;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserInterviewMetricsRepository extends JpaRepository<UserInterviewMetrics, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserInterviewMetrics> findByUser_Id(UUID userId);
}
