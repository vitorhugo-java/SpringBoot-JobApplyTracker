package com.jobtracker.repository;

import com.jobtracker.entity.GoogleDriveBaseResume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoogleDriveBaseResumeRepository extends JpaRepository<GoogleDriveBaseResume, UUID> {

    List<GoogleDriveBaseResume> findAllByConnectionIdOrderByCreatedAtAsc(UUID connectionId);

    Optional<GoogleDriveBaseResume> findByIdAndConnectionUserId(UUID id, UUID userId);
}
