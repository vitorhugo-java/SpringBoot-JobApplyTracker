package com.jobtracker.mcp.audit;

/**
 * Immutable description of a single audited MCP operation, assembled by {@code McpAuditAspect}
 * and handed to {@code McpAuditService} for metric emission and logging.
 *
 * @param action         the audited action / tool name (e.g. {@code "Create-Application"})
 * @param provider       the normalized MCP provider
 * @param clientName     the raw MCP client name (e.g. {@code "claude-code"}), may be null
 * @param clientVersion  the raw MCP client version (e.g. {@code "2.1.177"}), may be null
 * @param methodName     the intercepted Java method name
 * @param requestPayload the tool arguments (minus framework params) used for token/byte sizing
 * @param response       the tool's return value (null for {@code void} or on error)
 * @param status         SUCCESS or ERROR
 * @param errorMessage   the exception message when {@code status == ERROR}, otherwise null
 * @param durationMs     wall-clock execution time in milliseconds
 */
public record McpAuditEvent(
        String action,
        McpProvider provider,
        String clientName,
        String clientVersion,
        String methodName,
        Object requestPayload,
        Object response,
        AuditStatus status,
        String errorMessage,
        long durationMs) {
}
