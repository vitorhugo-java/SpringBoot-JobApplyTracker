package com.jobtracker.repository;

import com.jobtracker.entity.InterviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InterviewEventRepository extends JpaRepository<InterviewEvent, UUID> {

    long countByUser_Id(UUID userId);
}
