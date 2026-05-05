package com.jobtracker.repository;

import com.jobtracker.entity.GoogleDriveOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface GoogleDriveOAuthStateRepository extends JpaRepository<GoogleDriveOAuthState, UUID> {

    Optional<GoogleDriveOAuthState> findByStateToken(String stateToken);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
