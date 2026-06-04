package com.jobtracker.mcp.tools;

import com.jobtracker.dto.application.ApplicationPageResponse;
import com.jobtracker.dto.application.ApplicationRequest;
import com.jobtracker.dto.application.ApplicationResponse;
import com.jobtracker.dto.application.MarkDmSentRequest;
import com.jobtracker.dto.application.UpdateReminderRequest;
import com.jobtracker.dto.application.UpdateStatusRequest;
import com.jobtracker.service.ApplicationService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class McpApplicationTools {

    private final ApplicationService applicationService;

    public McpApplicationTools(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // --- Read tools ---

    @McpTool(description = """
            List job applications with optional filters. All parameters are optional.
            status: display name of the status, e.g. "RH", "Teste Técnico", "Rejeitado".
            recruiterName: partial name match.
            applicationDateFrom / applicationDateTo: date range in yyyy-MM-dd format.
            interviewScheduled: true to show only applications with a scheduled interview.
            archived: true to include archived applications (default: false / active only).
            page: 0-based page number (default 0). size: page size (default 20).
            sort: field,direction — e.g. "createdAt,desc" (default) or "applicationDate,asc".
            """, name = "List-Applications")
    public ApplicationPageResponse listApplications(
            @McpToolParam(description = "Status filter — display name, e.g. 'RH' or 'Teste Técnico'") String status,
            @McpToolParam(description = "Recruiter name partial match") String recruiterName,
            @McpToolParam(description = "Application date range start yyyy-MM-dd (inclusive)") String applicationDateFrom,
            @McpToolParam(description = "Application date range end yyyy-MM-dd (inclusive)") String applicationDateTo,
            @McpToolParam(description = "Filter by interview scheduled flag") Boolean interviewScheduled,
            @McpToolParam(description = "Include archived applications (default false)") Boolean archived,
            @McpToolParam(description = "Page number 0-based (default 0)") Integer page,
            @McpToolParam(description = "Page size (default 20)") Integer size,
            @McpToolParam(description = "Sort field,direction e.g. createdAt,desc") String sort) {
        LocalDate from = applicationDateFrom != null ? LocalDate.parse(applicationDateFrom) : null;
        LocalDate to   = applicationDateTo   != null ? LocalDate.parse(applicationDateTo)   : null;
        return applicationService.getAll(
                status,
                recruiterName,
                from, to,
                interviewScheduled,
                null,
                archived,
                page != null ? page : 0,
                size != null ? size : 20,
                sort  != null ? sort  : "createdAt,desc");
    }

    @McpTool(description = "Retrieve a single job application by its UUID.", name = "Get-Application")
    public ApplicationResponse getApplication(
            @McpToolParam(description = "Application UUID") String id) {
        return applicationService.getById(UUID.fromString(id));
    }

    @McpTool(description = "List applications with upcoming next-step reminders that have not yet passed.", name = "Get-Upcoming-Applications")
    public List<ApplicationResponse> getUpcomingApplications() {
        return applicationService.getUpcoming();
    }

    @McpTool(description = "List applications whose next-step deadline has passed with no status update.", name = "Get-Overdue-Applications")
    public List<ApplicationResponse> getOverdueApplications() {
        return applicationService.getOverdue();
    }

    // --- Write tools ---

    @McpTool(description = """
            Create a new job application.
            Required booleans: rhAcceptedConnection, interviewScheduled, recruiterDmReminderEnabled.
            Optional: vacancyName, recruiterName, organization, vacancyLink,
            applicationDate (yyyy-MM-dd), nextStepDateTime (yyyy-MM-ddTHH:mm:ss), status, note.
            Field defaults and invariants: resource://job-apply-tracker/application-creation-rules.
            Valid status values: resource://job-apply-tracker/application-statuses.
            """, name = "Create-Application")
    public ApplicationResponse createApplication(
            @McpToolParam(description = "Job title or vacancy name") String vacancyName,
            @McpToolParam(description = "Recruiter name") String recruiterName,
            @McpToolParam(description = "Company or organization name") String organization,
            @McpToolParam(description = "URL to the vacancy posting") String vacancyLink,
            @McpToolParam(description = "Date applied yyyy-MM-dd (null = today)") String applicationDate,
            @McpToolParam(description = "Whether the recruiter accepted a LinkedIn connection") Boolean rhAcceptedConnection,
            @McpToolParam(description = "Whether an interview has been scheduled") Boolean interviewScheduled,
            @McpToolParam(description = "Next follow-up date/time yyyy-MM-ddTHH:mm:ss") String nextStepDateTime,
            @McpToolParam(description = "Status display name — omit for no status") String status,
            @McpToolParam(description = "Whether a DM reminder to the recruiter is enabled") Boolean recruiterDmReminderEnabled,
            @McpToolParam(description = "Personal notes about this application") String note) {
        return applicationService.create(new ApplicationRequest(
                vacancyName,
                recruiterName,
                organization,
                vacancyLink,
                applicationDate != null ? LocalDate.parse(applicationDate) : null,
                rhAcceptedConnection != null ? rhAcceptedConnection : Boolean.FALSE,
                interviewScheduled != null ? interviewScheduled : Boolean.FALSE,
                nextStepDateTime != null ? LocalDateTime.parse(nextStepDateTime) : null,
                status,
                recruiterDmReminderEnabled != null ? recruiterDmReminderEnabled : Boolean.FALSE,
                note));
    }

    @McpTool(description = """
            Update all fields of an existing job application.
            Same field set as Create-Application. Invariants: resource://job-apply-tracker/application-creation-rules.
            """, name = "Update-Application")
    public ApplicationResponse updateApplication(
            @McpToolParam(description = "Application UUID to update") String id,
            @McpToolParam(description = "Job title or vacancy name") String vacancyName,
            @McpToolParam(description = "Recruiter name") String recruiterName,
            @McpToolParam(description = "Company or organization name") String organization,
            @McpToolParam(description = "URL to the vacancy posting") String vacancyLink,
            @McpToolParam(description = "Date applied yyyy-MM-dd") String applicationDate,
            @McpToolParam(description = "Whether the recruiter accepted a LinkedIn connection") Boolean rhAcceptedConnection,
            @McpToolParam(description = "Whether an interview has been scheduled") Boolean interviewScheduled,
            @McpToolParam(description = "Next follow-up date/time yyyy-MM-ddTHH:mm:ss") String nextStepDateTime,
            @McpToolParam(description = "Status display name") String status,
            @McpToolParam(description = "Whether a DM reminder to the recruiter is enabled") Boolean recruiterDmReminderEnabled,
            @McpToolParam(description = "Personal notes about this application") String note) {
        return applicationService.update(UUID.fromString(id), new ApplicationRequest(
                vacancyName,
                recruiterName,
                organization,
                vacancyLink,
                applicationDate != null ? LocalDate.parse(applicationDate) : null,
                rhAcceptedConnection != null ? rhAcceptedConnection : Boolean.FALSE,
                interviewScheduled != null ? interviewScheduled : Boolean.FALSE,
                nextStepDateTime != null ? LocalDateTime.parse(nextStepDateTime) : null,
                status,
                recruiterDmReminderEnabled != null ? recruiterDmReminderEnabled : Boolean.FALSE,
                note));
    }

    @McpTool(description = """
            Update only the status of a job application.
            Valid status names: resource://job-apply-tracker/application-statuses.
            """, name = "Update-Application-Status")
    public void updateApplicationStatus(
            @McpToolParam(description = "Application UUID") String id,
            @McpToolParam(description = "New status display name") String status) {
        applicationService.updateStatus(UUID.fromString(id), new UpdateStatusRequest(status));
    }

    @McpTool(description = "Enable or disable the recruiter DM reminder for a job application.", name = "Update-Application-Reminder")
    public void updateApplicationReminder(
            @McpToolParam(description = "Application UUID") String id,
            @McpToolParam(description = "true to enable the DM reminder, false to disable it") boolean enabled) {
        applicationService.updateReminder(UUID.fromString(id), new UpdateReminderRequest(enabled));
    }

    @McpTool(description = "Mark that a LinkedIn DM was sent to the recruiter for a job application.", name = "Mark-Recruiter-DM-Sent")
    public void markRecruiterDmSent(
            @McpToolParam(description = "Application UUID") String id) {
        applicationService.markDmSent(UUID.fromString(id), new MarkDmSentRequest());
    }

    @McpTool(description = "Archive a job application so it is hidden from the default active list.", name = "Archive-Application")
    public void archiveApplication(
            @McpToolParam(description = "Application UUID") String id) {
        applicationService.archive(UUID.fromString(id));
    }

    @McpTool(description = "Permanently delete a job application.", name = "Delete-Application")
    public void deleteApplication(
            @McpToolParam(description = "Application UUID") String id) {
        applicationService.delete(UUID.fromString(id));
    }
}
