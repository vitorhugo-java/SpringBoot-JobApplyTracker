package com.jobtracker.repository;

import com.jobtracker.entity.GptOAuthAuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface GptOAuthAuthorizationCodeRepository extends JpaRepository<GptOAuthAuthorizationCode, UUID> {

    Optional<GptOAuthAuthorizationCode> findByCodeHash(String codeHash);

    void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
