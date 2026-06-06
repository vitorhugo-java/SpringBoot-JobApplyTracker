package com.jobtracker.mcp.tools;

import com.jobtracker.dto.application.ApplicationPageResponse;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.application.ApplicationResponse;
import com.jobtracker.dto.application.MarkDmSentRequest;
import com.jobtracker.dto.application.UpdateReminderRequest;
import com.jobtracker.dto.application.UpdateStatusRequest;
import com.jobtracker.service.ApplicationService;
import com.jobtracker.service.ToolMetricsCollector;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class McpApplicationTools {

    private final ApplicationService applicationService;
    private final ToolMetricsCollector metricsCollector;

    public McpApplicationTools(ApplicationService applicationService,
                               ToolMetricsCollector metricsCollector) {
        this.applicationService = applicationService;
        this.metricsCollector = metricsCollector;
    }

    // --- Read tools ---

    @McpTool(
            name = "List-Applications",
            title = "List Applications",
            description = "List job applications with optional filters and pagination.",
            annotations = @McpAnnotations(
                    title = "List Applications",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ApplicationPageResponse listApplications(
            @McpToolParam(required = false, description = "Status filter — display name, e.g. 'RH' or 'Teste Técnico'") String status,
            @McpToolParam(required = false, description = "Recruiter name partial match") String recruiterName,
            @McpToolParam(required = false, description = "Application date range start yyyy-MM-dd (inclusive)") String applicationDateFrom,
            @McpToolParam(required = false, description = "Application date range end yyyy-MM-dd (inclusive)") String applicationDateTo,
            @McpToolParam(required = false, description = "Filter by interview scheduled flag") Boolean interviewScheduled,
            @McpToolParam(required = false, description = "Include archived applications (default false)") Boolean archived,
            @McpToolParam(required = false, description = "Page number 0-based (default 0)") Integer page,
            @McpToolParam(required = false, description = "Page size (default 20)") Integer size,
            @McpToolParam(required = false, description = "Sort field,direction e.g. createdAt,desc") String sort) {
        LocalDate from = applicationDateFrom != null ? LocalDate.parse(applicationDateFrom) : null;
        LocalDate to   = applicationDateTo   != null ? LocalDate.parse(applicationDateTo)   : null;
        int       p    = page != null ? page : 0;
        int       s    = size != null ? size : 20;
        String    so   = sort != null ? sort : "createdAt,desc";

        return metricsCollector.measure(
                "List-Applications",
                params("status", status, "recruiterName", recruiterName,
                        "from", applicationDateFrom, "to", applicationDateTo,
                        "page", p, "size", s, "sort", so),
                () -> applicationService.getAll(status, recruiterName, from, to, interviewScheduled, null, archived, p, s, so));
    }

    @McpTool(
            name = "Get-Application",
            title = "Get Application",
            description = "Fetch a single job application by UUID.",
            annotations = @McpAnnotations(
                    title = "Get Application",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ApplicationResponse getApplication(
            @McpToolParam(required = true, description = "Application UUID") String id) {
        return metricsCollector.measure(
                "Get-Application",
                params("id", id),
                () -> applicationService.getById(UUID.fromString(id)));
    }

    @McpTool(
            name = "Get-Upcoming-Applications",
            title = "Get Upcoming Applications",
            description = "List applications with upcoming reminders that have not yet passed.",
            annotations = @McpAnnotations(
                    title = "Get Upcoming Applications",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public List<ApplicationResponse> getUpcomingApplications() {
        return metricsCollector.measure(
                "Get-Upcoming-Applications",
                null,
                applicationService::getUpcoming);
    }

    @McpTool(
            name = "Get-Overdue-Applications",
            title = "Get Overdue Applications",
            description = "List applications whose follow-up deadline has passed.",
            annotations = @McpAnnotations(
                    title = "Get Overdue Applications",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public List<ApplicationResponse> getOverdueApplications() {
        return metricsCollector.measure(
                "Get-Overdue-Applications",
                null,
                applicationService::getOverdue);
    }

    // --- Write tools ---

    @McpTool(
            name = "Create-Application",
            title = "Create Application",
            description = "Create a new job application record.",
            annotations = @McpAnnotations(
                    title = "Create Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ApplicationResponse createApplication(
            @McpToolParam(required = false, description = "Job title or vacancy name") String vacancyName,
            @McpToolParam(required = false, description = "Recruiter name") String recruiterName,
            @McpToolParam(required = false, description = "Company or organization name") String organization,
            @McpToolParam(required = false, description = "URL to the vacancy posting") String vacancyLink,
            @McpToolParam(required = false, description = "Date applied yyyy-MM-dd (null = today)") String applicationDate,
            @McpToolParam(required = true, description = "Whether the recruiter accepted a LinkedIn connection") Boolean rhAcceptedConnection,
            @McpToolParam(required = true, description = "Whether an interview has been scheduled") Boolean interviewScheduled,
            @McpToolParam(required = false, description = "Next follow-up date/time yyyy-MM-ddTHH:mm:ss") String nextStepDateTime,
            @McpToolParam(required = false, description = "Status display name — omit for no status") String status,
            @McpToolParam(required = true, description = "Whether a DM reminder to the recruiter is enabled") Boolean recruiterDmReminderEnabled,
            @McpToolParam(required = false, description = "Personal notes about this application") String note,
            @McpToolParam(required = false, description = "Platform or job board where the vacancy was found, e.g. LinkedIn, Gupy, Indeed, Catho") String platform) {
        ApplicationRequest request = new ApplicationRequest(
                vacancyName, recruiterName, organization, vacancyLink,
                applicationDate != null ? LocalDate.parse(applicationDate) : null,
                rhAcceptedConnection != null ? rhAcceptedConnection : Boolean.FALSE,
                interviewScheduled != null ? interviewScheduled : Boolean.FALSE,
                nextStepDateTime != null ? LocalDateTime.parse(nextStepDateTime) : null,
                status,
                recruiterDmReminderEnabled != null ? recruiterDmReminderEnabled : Boolean.FALSE,
                note, platform, null);
        return metricsCollector.measure("Create-Application", request, () -> applicationService.create(request));
    }

    @McpTool(
            name = "Update-Application",
            title = "Update Application",
            description = "Update all fields on an existing job application.",
            annotations = @McpAnnotations(
                    title = "Update Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ApplicationResponse updateApplication(
            @McpToolParam(required = true, description = "Application UUID to update") String id,
            @McpToolParam(required = false, description = "Job title or vacancy name") String vacancyName,
            @McpToolParam(required = false, description = "Recruiter name") String recruiterName,
            @McpToolParam(required = false, description = "Company or organization name") String organization,
            @McpToolParam(required = false, description = "URL to the vacancy posting") String vacancyLink,
            @McpToolParam(required = false, description = "Date applied yyyy-MM-dd") String applicationDate,
            @McpToolParam(required = true, description = "Whether the recruiter accepted a LinkedIn connection") Boolean rhAcceptedConnection,
            @McpToolParam(required = true, description = "Whether an interview has been scheduled") Boolean interviewScheduled,
            @McpToolParam(required = false, description = "Next follow-up date/time yyyy-MM-ddTHH:mm:ss") String nextStepDateTime,
            @McpToolParam(required = false, description = "Status display name") String status,
            @McpToolParam(required = true, description = "Whether a DM reminder to the recruiter is enabled") Boolean recruiterDmReminderEnabled,
            @McpToolParam(required = false, description = "Personal notes about this application") String note,
            @McpToolParam(required = false, description = "Platform or job board where the vacancy was found, e.g. LinkedIn, Gupy, Indeed, Catho") String platform) {
        ApplicationRequest request = new ApplicationRequest(
                vacancyName, recruiterName, organization, vacancyLink,
                applicationDate != null ? LocalDate.parse(applicationDate) : null,
                rhAcceptedConnection != null ? rhAcceptedConnection : Boolean.FALSE,
                interviewScheduled != null ? interviewScheduled : Boolean.FALSE,
                nextStepDateTime != null ? LocalDateTime.parse(nextStepDateTime) : null,
                status,
                recruiterDmReminderEnabled != null ? recruiterDmReminderEnabled : Boolean.FALSE,
                note, platform, null);
        return metricsCollector.measure(
                "Update-Application",
                params("id", id, "request", request),
                () -> applicationService.update(UUID.fromString(id), request));
    }

    @McpTool(
            name = "Update-Application-Status",
            title = "Update Application Status",
            description = "Update only the status of an existing job application.",
            annotations = @McpAnnotations(
                    title = "Update Application Status",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ApplicationResponse updateApplicationStatus(
            @McpToolParam(required = true, description = "Application UUID") String id,
            @McpToolParam(required = true, description = "New status display name") String status) {
        return metricsCollector.measure(
                "Update-Application-Status",
                params("id", id, "status", status),
                () -> applicationService.updateStatus(UUID.fromString(id), new UpdateStatusRequest(status)));
    }

    @McpTool(
            name = "Update-Application-Reminder",
            title = "Update Application Reminder",
            description = "Enable or disable the recruiter DM reminder for an application.",
            annotations = @McpAnnotations(
                    title = "Update Application Reminder",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public void updateApplicationReminder(
            @McpToolParam(required = true, description = "Application UUID") String id,
            @McpToolParam(required = true, description = "true to enable the DM reminder, false to disable it") boolean enabled) {
        metricsCollector.measure(
                "Update-Application-Reminder",
                params("id", id, "enabled", enabled),
                () -> { applicationService.updateReminder(UUID.fromString(id), new UpdateReminderRequest(enabled)); return null; });
    }

    @McpTool(
            name = "Mark-Recruiter-DM-Sent",
            title = "Mark Recruiter DM Sent",
            description = "Record that a recruiter DM was sent for an application.",
            annotations = @McpAnnotations(
                    title = "Mark Recruiter DM Sent",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public void markRecruiterDmSent(
            @McpToolParam(required = true, description = "Application UUID") String id) {
        metricsCollector.measure(
                "Mark-Recruiter-DM-Sent",
                params("id", id),
                () -> { applicationService.markDmSent(UUID.fromString(id), new MarkDmSentRequest()); return null; });
    }

    @McpTool(
            name = "Archive-Application",
            title = "Archive Application",
            description = "Archive an application so it is hidden from the default active list.",
            annotations = @McpAnnotations(
                    title = "Archive Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public void archiveApplication(
            @McpToolParam(required = true, description = "Application UUID") String id) {
        metricsCollector.measure(
                "Archive-Application",
                params("id", id),
                () -> { applicationService.archive(UUID.fromString(id)); return null; });
    }

    @McpTool(
            name = "Delete-Application",
            title = "Delete Application",
            description = "Permanently delete an application.",
            annotations = @McpAnnotations(
                    title = "Delete Application",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false,
                    openWorldHint = false))
    public void deleteApplication(
            @McpToolParam(required = true, description = "Application UUID") String id) {
        metricsCollector.measure(
                "Delete-Application",
                params("id", id),
                () -> { applicationService.delete(UUID.fromString(id)); return null; });
    }

    // --- helpers ---

    /** Builds a null-safe parameter map for use as the request descriptor in measure(). */
    private static Map<String, Object> params(Object... kvPairs) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            if (kvPairs[i + 1] != null) {
                map.put(kvPairs[i].toString(), kvPairs[i + 1]);
            }
        }
        return map;
    }
}
