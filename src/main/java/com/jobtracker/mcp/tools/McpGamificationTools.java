package com.jobtracker.mcp.tools;

import com.jobtracker.dto.gamification.GamificationEventRequest;
import com.jobtracker.dto.gamification.GamificationEventSummary;
import com.jobtracker.entity.enums.GamificationEventType;
import com.jobtracker.gamification.GamificationProgressCallback;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.service.GamificationService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class McpGamificationTools {

    private final GamificationService gamificationService;

    public McpGamificationTools(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @McpTool(
            name = "Apply-Gamification-Event",
            title = "Apply Gamification Event",
            description = """
                    Apply a gamification event for the authenticated user and return an aggregated summary
                    that includes XP gained, new level, streak changes, and any newly unlocked achievements.
                    Emits incremental progress notifications during processing when the client supports it.
                    """,
            annotations = @McpAnnotations(
                    title = "Apply Gamification Event",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    @AuditMcpOperation(action = "Apply-Gamification-Event")
    public GamificationEventSummary applyGamificationEvent(
            McpSyncRequestContext mcpContext,
            @McpToolParam(required = true,
                    description = "Event type: APPLICATION_CREATED, RECRUITER_DM_SENT, INTERVIEW_PROGRESS, NOTE_ADDED, OFFER_WON")
            String eventType,
            @McpToolParam(required = false,
                    description = "UUID of the related application — required for application-scoped events")
            String applicationId,
            @McpToolParam(required = false,
                    description = "ISO-8601 timestamp when the event occurred (defaults to now), e.g. 2025-06-05T10:30:00")
            String occurredAt) {

        GamificationEventType type = GamificationEventType.valueOf(eventType.trim().toUpperCase());
        UUID appId = applicationId != null && !applicationId.isBlank() ? UUID.fromString(applicationId) : null;
        LocalDateTime when = occurredAt != null && !occurredAt.isBlank() ? LocalDateTime.parse(occurredAt) : null;

        GamificationEventRequest request = new GamificationEventRequest(type, appId, when);

        GamificationProgressCallback callback = buildCallback(mcpContext);
        return gamificationService.applyEventWithProgress(request, callback);
    }

    private GamificationProgressCallback buildCallback(McpSyncRequestContext ctx) {
        if (ctx == null) {
            return (step, total, message) -> {};
        }
        return (step, total, message) -> {
            try {
                ctx.progress(spec -> spec.progress(step).total(total).message(message));
            } catch (Exception ignored) {
                // progress notifications are best-effort; a missing progressToken is not fatal
            }
        };
    }
}
