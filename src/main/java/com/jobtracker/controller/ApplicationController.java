package com.jobtracker.controller;

import com.jobtracker.dto.application.*;
import com.jobtracker.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(applicationService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(applicationService.updateStatus(id, request));
    }

    @PatchMapping("/{id}/reminder")
    public ResponseEntity<ApplicationResponse> updateReminder(@PathVariable Long id,
                                                               @Valid @RequestBody UpdateReminderRequest request) {
        return ResponseEntity.ok(applicationService.updateReminder(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Application deleted successfully"));
    }

    @GetMapping
    public ResponseEntity<ApplicationPageResponse> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recruiterName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationDateTo,
            @RequestParam(required = false) Boolean interviewScheduled,
            @RequestParam(required = false) Boolean recruiterDmReminderEnabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(applicationService.getAll(status, recruiterName, applicationDateFrom,
                applicationDateTo, interviewScheduled, recruiterDmReminderEnabled, page, size, sort));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ApplicationResponse>> getUpcoming() {
        return ResponseEntity.ok(applicationService.getUpcoming());
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<ApplicationResponse>> getOverdue() {
        return ResponseEntity.ok(applicationService.getOverdue());
    }
}
