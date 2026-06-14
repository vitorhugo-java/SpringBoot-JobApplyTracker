package com.jobtracker.mcp.tools;

import com.jobtracker.dto.application.ApplicationFilter;
import com.jobtracker.dto.application.ApplicationPageResponse;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.application.ApplicationResponse;
import com.jobtracker.dto.application.MarkDmSentRequest;
import com.jobtracker.dto.application.UpdateReminderRequest;
import com.jobtracker.dto.application.UpdateStatusRequest;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.ApplicationService;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class McpApplicationTools {

    private final ApplicationService applicationService;

    public McpApplicationTools(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // --- Status tools ---

    @McpTool(
            name = "List-Statuses",
            title = "List Statuses",
            description = "Returns all valid application status values. ALWAYS call this before setting any status. Never hardcode or assume status values.",
            annotations = @McpAnnotations(
                    title = "List Statuses",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    @AuditMcpOperation(action = "List-Statuses")
    public List<String> listStatuses(McpSyncRequestContext ctx) {
        return applicationService.listStatuses();
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
    @AuditMcpOperation(action = "List-Applications")
    public ApplicationPageResponse listApplications(
            McpSyncRequestContext ctx,
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

        ApplicationFilter filter = new ApplicationFilter(
                null, status, null, recruiterName, null, null, null,
                from, to, null, null, interviewScheduled, null, null, null, null, null, archived);

        return applicationService.getAll(filter, p, s, so);
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
    @AuditMcpOperation(action = "Get-Application")
    public ApplicationResponse getApplication(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "Application UUID") String id) {
        return applicationService.getById(UUID.fromString(id));
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
    @AuditMcpOperation(action = "Get-Upcoming-Applications")
    public List<ApplicationResponse> getUpcomingApplications(McpSyncRequestContext ctx) {
        return applicationService.getUpcoming();
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
    @AuditMcpOperation(action = "Get-Overdue-Applications")
    public List<ApplicationResponse> getOverdueApplications(McpSyncRequestContext ctx) {
        return applicationService.getOverdue();
    }

    // --- Write tools ---

    @McpTool(
            name = "Create-Application",
            title = "Create Application",
            description = "Create a new job application record. IMPORTANT: Call List-Statuses first to get valid status values. Never use a status value from memory.",
            annotations = @McpAnnotations(
                    title = "Create Application",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    @AuditMcpOperation(action = "Create-Application")
    public ApplicationResponse createApplication(
            McpSyncRequestContext ctx,
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
        return applicationService.create(request);
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
    @AuditMcpOperation(action = "Update-Application")
    public ApplicationResponse updateApplication(
            McpSyncRequestContext ctx,
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
        return applicationService.update(UUID.fromString(id), request);
    }

    @McpTool(
            name = "Update-Application-Status",
            title = "Update Application Status",
            description = "Update only the status of an existing job application. IMPORTANT: Call List-Statuses first to get valid status values. Never use a status value from memory.",
            annotations = @McpAnnotations(
                    title = "Update Application Status",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    @AuditMcpOperation(action = "Update-Application-Status")
    public ApplicationResponse updateApplicationStatus(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "Application UUID") String id,
            @McpToolParam(required = true, description = "New status display name") String status) {
        return applicationService.updateStatus(UUID.fromString(id), new UpdateStatusRequest(status));
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
    @AuditMcpOperation(action = "Update-Application-Reminder")
    public void updateApplicationReminder(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "Application UUID") String id,
            @McpToolParam(required = true, description = "true to enable the DM reminder, false to disable it") boolean enabled) {
        applicationService.updateReminder(UUID.fromString(id), new UpdateReminderRequest(enabled));
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
    @AuditMcpOperation(action = "Mark-Recruiter-DM-Sent")
    public void markRecruiterDmSent(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "Application UUID") String id) {
        applicationService.markDmSent(UUID.fromString(id), new MarkDmSentRequest());
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
    @AuditMcpOperation(action = "Archive-Application")
    public void archiveApplication(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "Application UUID") String id) {
        applicationService.archive(UUID.fromString(id));
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
    @AuditMcpOperation(action = "Delete-Application")
    public void deleteApplication(
            McpSyncRequestContext ctx,
            @McpToolParam(required = true, description = "Application UUID") String id) {
        applicationService.delete(UUID.fromString(id));
    }

    // --- diagnostics ---

    @McpTool(
            name = "Ping",
            title = "Ping",
            description = "Returns 'hello'. Use this to verify MCP transport connectivity before calling data tools.",
            annotations = @McpAnnotations(
                    title = "Ping",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    @AuditMcpOperation(action = "Ping")
    public String ping(McpSyncRequestContext ctx) {
        return "hello";
    }
}
