package com.jobtracker.service;

import com.jobtracker.mcp.audit.AuditStatus;
import com.jobtracker.mcp.audit.McpAuditEvent;
import com.jobtracker.mcp.audit.McpProvider;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Emits observability signals for audited MCP operations: Micrometer meters
 * (scraped by Prometheus, graphed in Grafana) plus a structured DEBUG audit log line.
 *
 * <p>This service performs <strong>no database persistence</strong> — the audit trail lives in
 * Prometheus (aggregate metrics) and the application logs (per-call detail incl. user email).
 *
 * <p>Meter tags are deliberately low-cardinality ({@code action}, {@code provider},
 * {@code status}); high-cardinality detail (user email, error text) is confined to the log line
 * so Prometheus label cardinality cannot explode.
 *
 * <p>Recording is fire-and-forget: any metric/log failure is caught and swallowed so auditing
 * can never interrupt business execution.
 */
@Service
public class McpAuditService {

    private static final Logger log = LoggerFactory.getLogger(McpAuditService.class);
    private static final int EXPENSIVE_TOKEN_THRESHOLD = 5_000;

    private static final String METER_INVOCATIONS = "mcp.tool.invocations";
    private static final String METER_DURATION = "mcp.tool.duration";
    private static final String METER_TOKENS = "mcp.tool.tokens";

    private final MeterRegistry meterRegistry;
    private final TokenEstimatorService tokenEstimator;
    private final SecurityUtils securityUtils;

    public McpAuditService(MeterRegistry meterRegistry,
                           TokenEstimatorService tokenEstimator,
                           SecurityUtils securityUtils) {
        this.meterRegistry = meterRegistry;
        this.tokenEstimator = tokenEstimator;
        this.securityUtils = securityUtils;
    }

    /**
     * Records a single audited MCP operation: increments the invocation counter, records the
     * duration timer, publishes token-size summaries, and writes a DEBUG audit log line.
     */
    public void record(McpAuditEvent event) {
        try {
            McpProvider provider = event.provider() != null ? event.provider() : McpProvider.UNKNOWN;
            String action = event.action();
            String status = event.status().name();

            int requestTokens = tokenEstimator.countTokens(event.requestPayload(), provider.encoding());
            int requestBytes = tokenEstimator.countBytes(event.requestPayload());
            int responseTokens = tokenEstimator.countTokens(event.response(), provider.encoding());
            int responseBytes = tokenEstimator.countBytes(event.response());
            int totalTokens = requestTokens + responseTokens;
            boolean expensive = totalTokens > EXPENSIVE_TOKEN_THRESHOLD;

            emitMeters(action, provider.name(), status, event.durationMs(),
                    requestTokens, responseTokens, totalTokens);

            logAudit(event, provider, requestTokens, responseTokens, requestBytes, responseBytes, expensive);
        } catch (Exception e) {
            // Auditing is best-effort and must never break the tool call.
            log.debug("[MCP-AUDIT] Failed to record audit event for action={}: {}",
                    event != null ? event.action() : "<null>", e.getMessage());
        }
    }

    private void emitMeters(String action, String provider, String status, long durationMs,
                            int requestTokens, int responseTokens, int totalTokens) {
        Counter.builder(METER_INVOCATIONS)
                .description("Count of MCP tool invocations")
                .tag("action", action)
                .tag("provider", provider)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer.builder(METER_DURATION)
                .description("MCP tool execution time")
                .tag("action", action)
                .tag("provider", provider)
                .tag("status", status)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));

        recordTokens(action, provider, "total", totalTokens);
        recordTokens(action, provider, "request", requestTokens);
        recordTokens(action, provider, "response", responseTokens);
    }

    private void recordTokens(String action, String provider, String kind, int tokens) {
        DistributionSummary.builder(METER_TOKENS)
                .description("Estimated token usage for MCP tool calls")
                .tag("action", action)
                .tag("provider", provider)
                .tag("kind", kind)
                .register(meterRegistry)
                .record(tokens);
    }

    private void logAudit(McpAuditEvent event, McpProvider provider,
                          int requestTokens, int responseTokens,
                          int requestBytes, int responseBytes, boolean expensive) {
        String userEmail = resolveCurrentUserEmail();
        if (event.status() == AuditStatus.ERROR) {
            log.debug("[MCP-AUDIT] action={} provider={} clientName={} clientVersion={} userEmail={} "
                            + "status=ERROR durationMs={} reqTokens={} respTokens={} reqBytes={} respBytes={} error={}",
                    event.action(), provider, event.clientName(), event.clientVersion(), userEmail,
                    event.durationMs(), requestTokens, responseTokens, requestBytes, responseBytes,
                    event.errorMessage());
        } else {
            log.debug("[MCP-AUDIT] action={} provider={} clientName={} clientVersion={} userEmail={} "
                            + "status=SUCCESS durationMs={} reqTokens={} respTokens={} reqBytes={} respBytes={} expensive={}",
                    event.action(), provider, event.clientName(), event.clientVersion(), userEmail,
                    event.durationMs(), requestTokens, responseTokens, requestBytes, responseBytes, expensive);
        }
        if (expensive) {
            log.warn("[MCP-AUDIT] High-token operation — action={} provider={} totalTokens={}",
                    event.action(), provider, requestTokens + responseTokens);
        }
    }

    private String resolveCurrentUserEmail() {
        try {
            return securityUtils.getCurrentUser().getEmail();
        } catch (Exception e) {
            return "<unavailable>";
        }
    }
}
