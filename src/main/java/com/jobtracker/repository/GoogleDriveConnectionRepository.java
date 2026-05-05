package com.jobtracker.repository;

import com.jobtracker.entity.GoogleDriveConnection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleDriveConnectionRepository extends JpaRepository<GoogleDriveConnection, UUID> {

    @EntityGraph(attributePaths = "baseResumes")
    Optional<GoogleDriveConnection> findByUserId(UUID userId);
}
