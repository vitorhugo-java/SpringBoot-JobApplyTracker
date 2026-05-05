package com.jobtracker.repository;

import com.jobtracker.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findAllByUser_IdOrderByAchievedAtDesc(UUID userId);
}
