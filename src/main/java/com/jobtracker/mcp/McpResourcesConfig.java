package com.jobtracker.mcp;

import org.springframework.context.annotation.Configuration;

/**
 * Base configuration anchor for MCP resource beans.
 */
@Configuration
public class McpResourcesConfig {
    public static final String URI_PIPELINE_SUMMARY = "resource://job-apply-tracker/pipeline-summary";
    public static final String URI_GAMIFICATION_PROFILE = "resource://job-apply-tracker/gamification-profile";
    public static final String URI_ACHIEVEMENTS = "resource://job-apply-tracker/achievements";
    public static final String URI_DRIVE_STATUS = "resource://job-apply-tracker/drive-status";
    public static final String URI_BASE_RESUMES = "resource://job-apply-tracker/base-resumes";
    public static final String URI_CURRENT_USER = "resource://job-apply-tracker/current-user";
    public static final String URI_APPLICATION_CREATION_RULES = "resource://job-apply-tracker/application-creation-rules";
    public static final String URI_RESUME_WORKFLOW_RULES = "resource://job-apply-tracker/resume-workflow-rules";
    public static final String URI_APPLICATION_STATUSES = "resource://job-apply-tracker/application-statuses";
    public static final String URI_BASE_RESUME_CONTENT = "resource://job-apply-tracker/base-resume/{resumeId}";
    public static final String URI_GENERATED_RESUME_CONTENT = "resource://job-apply-tracker/generated-resume/{applicationId}";
}
