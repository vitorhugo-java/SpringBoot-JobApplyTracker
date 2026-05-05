package com.jobtracker.repository;

import com.jobtracker.entity.UserGamification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGamificationRepository extends JpaRepository<UserGamification, UUID> {
    Optional<UserGamification> findByUser_Id(UUID userId);
}
