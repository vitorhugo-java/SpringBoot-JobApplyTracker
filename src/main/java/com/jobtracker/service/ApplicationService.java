package com.jobtracker.service;

import com.jobtracker.dto.application.*;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.enums.ApplicationStatus;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.ResourceNotFoundException;
import com.jobtracker.mapper.ApplicationMapper;
import com.jobtracker.repository.ApplicationRepository;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplicationService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "applicationDate", "status",
            "vacancyName", "recruiterName", "nextStepDateTime"
    );

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final SecurityUtils securityUtils;
    private final Tracer tracer;

    public ApplicationService(ApplicationRepository applicationRepository,
                               ApplicationMapper applicationMapper,
                               SecurityUtils securityUtils,
                               Tracer tracer) {
        this.applicationRepository = applicationRepository;
        this.applicationMapper = applicationMapper;
        this.securityUtils = securityUtils;
        this.tracer = tracer;
    }

    @Transactional
    public ApplicationResponse create(ApplicationRequest request) {
        Span span = tracer.nextSpan().name("create-application").start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            JobApplication app = new JobApplication();
            mapRequestToEntity(request, app);
            app.setUser(securityUtils.getCurrentUser());
            return applicationMapper.toResponse(applicationRepository.save(app));
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        return applicationMapper.toResponse(app);
    }

    @Transactional
    public ApplicationResponse update(UUID id, ApplicationRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        mapRequestToEntity(request, app);
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID id, UpdateStatusRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setStatus(resolveStatus(request.status()));
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public ApplicationResponse updateReminder(UUID id, UpdateReminderRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        app.setRecruiterDmReminderEnabled(request.recruiterDmReminderEnabled());
        return applicationMapper.toResponse(applicationRepository.save(app));
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = securityUtils.getCurrentUserId();
        JobApplication app = applicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
        applicationRepository.delete(app);
    }

    @Transactional(readOnly = true)
    public ApplicationPageResponse getAll(String status, String recruiterName,
                                           LocalDate applicationDateFrom, LocalDate applicationDateTo,
                                           Boolean interviewScheduled, Boolean recruiterDmReminderEnabled,
                                           int page, int size, String sort) {
        UUID userId = securityUtils.getCurrentUserId();

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
        UUID userId = securityUtils.getCurrentUserId();
        return applicationRepository.findUpcomingByUserId(userId, LocalDateTime.now())
                .stream().map(applicationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getOverdue() {
        UUID userId = securityUtils.getCurrentUserId();
        return applicationRepository.findOverdueByUserId(userId, LocalDateTime.now())
                .stream().map(applicationMapper::toResponse).toList();
    }

    private void mapRequestToEntity(ApplicationRequest request, JobApplication app) {
        app.setVacancyName(normalizeOptionalText(request.vacancyName()));
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
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new BadRequestException("Invalid sort field: " + field +
                    ". Allowed fields: " + ALLOWED_SORT_FIELDS);
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Specification<JobApplication> buildSpecification(UUID userId, String status,
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
