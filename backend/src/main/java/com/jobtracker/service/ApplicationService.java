package com.jobtracker.service;

import com.jobtracker.dto.application.*;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.mapper.ApplicationMapper;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.util.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final SecurityUtils securityUtils;

    public ApplicationService(ApplicationRepository applicationRepository,
                               ApplicationMapper applicationMapper,
                               SecurityUtils securityUtils) {
        this.applicationRepository = applicationRepository;
        this.applicationMapper = applicationMapper;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public ApplicationResponse create(ApplicationRequest request) {
        JobApplication app = new JobApplication();
        mapRequestToEntity(request, app);
        app.setUser(securityUtils.getCurrentUser());
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(Long id) {
        Long userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return applicationMapper.toResponse(app);
    }

    @Transactional
    public ApplicationResponse update(Long id, ApplicationRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        mapRequestToEntity(request, app);
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public ApplicationResponse updateStatus(Long id, UpdateStatusRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setStatus(resolveStatus(request.status()));
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public ApplicationResponse updateReminder(Long id, UpdateReminderRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setRecruiterDmReminderEnabled(request.recruiterDmReminderEnabled());
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public void delete(Long id) {
        Long userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        applicationRepository.delete(app);
    }

    @Transactional(readOnly = true)
    public ApplicationPageResponse getAll(String status, String recruiterName,
                                           LocalDate applicationDateFrom, LocalDate applicationDateTo,
                                           Boolean interviewScheduled, Boolean recruiterDmReminderEnabled,
                                           int page, int size, String sort) {
        Long userId = securityUtils.getCurrentUserId();

        Sort sortObj = buildSort(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Specification<JobApplication> spec = buildSpecification(userId, status, recruiterName,
                applicationDateFrom, applicationDateTo, interviewScheduled, recruiterDmReminderEnabled);

        Page<JobApplication> resultPage = applicationRepository.findAll(spec, pageable);

        List<ApplicationResponse> content = resultPage.getContent()
                .stream().map(applicationMapper::toResponse).toList();

        return new ApplicationPageResponse(
                content,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getUpcoming() {
        Long userId = securityUtils.getCurrentUserId();
        return applicationRepository.findUpcomingByUserId(userId, LocalDateTime.now())
                .stream().map(applicationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getOverdue() {
        Long userId = securityUtils.getCurrentUserId();
        return applicationRepository.findOverdueByUserId(userId, LocalDateTime.now())
                .stream().map(applicationMapper::toResponse).toList();
    }

    private void mapRequestToEntity(ApplicationRequest request, JobApplication app) {
        app.setVacancyName(request.vacancyName());
        app.setRecruiterName(request.recruiterName());
        app.setVacancyOpenedBy(request.vacancyOpenedBy());
        app.setVacancyLink(request.vacancyLink());
        app.setApplicationDate(request.applicationDate());
        app.setRhAcceptedConnection(Boolean.TRUE.equals(request.rhAcceptedConnection()));
        app.setInterviewScheduled(Boolean.TRUE.equals(request.interviewScheduled()));
        app.setNextStepDateTime(request.nextStepDateTime());
        app.setStatus(resolveStatus(request.status()));
        app.setRecruiterDmReminderEnabled(Boolean.TRUE.equals(request.recruiterDmReminderEnabled()));
    }

    private ApplicationStatus resolveStatus(String statusName) {
        try {
            return ApplicationStatus.fromDisplayName(statusName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status value: " + statusName);
        }
    }

    private Sort buildSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private Specification<JobApplication> buildSpecification(Long userId, String status,
                                                               String recruiterName,
                                                               LocalDate applicationDateFrom,
                                                               LocalDate applicationDateTo,
                                                               Boolean interviewScheduled,
                                                               Boolean recruiterDmReminderEnabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (status != null && !status.isBlank()) {
                try {
                    ApplicationStatus appStatus = ApplicationStatus.fromDisplayName(status);
                    predicates.add(cb.equal(root.get("status"), appStatus));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid status filter: " + status);
                }
            }

            if (recruiterName != null && !recruiterName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("recruiterName")),
                        "%" + recruiterName.toLowerCase() + "%"));
            }

            if (applicationDateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("applicationDate"), applicationDateFrom));
            }

            if (applicationDateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("applicationDate"), applicationDateTo));
            }

            if (interviewScheduled != null) {
                predicates.add(cb.equal(root.get("interviewScheduled"), interviewScheduled));
            }

            if (recruiterDmReminderEnabled != null) {
                predicates.add(cb.equal(root.get("recruiterDmReminderEnabled"), recruiterDmReminderEnabled));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
