package com.jobtracker.service;

import com.jobtracker.entity.ToolExecutionMetric;
import com.jobtracker.repository.ToolExecutionMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Generic wrapper that measures MCP tool executions and stores the resulting metrics.
 *
 * <p>Metrics collection is fire-and-forget: any persistence failure is logged and
 * swallowed so that a metrics error can never interrupt business execution.
 *
 * <p>Usage:
 * <pre>{@code
 * return metricsCollector.measure("List-Applications", requestParams, () ->
 *         applicationService.getAll(...));
 * }</pre>
 */
@Service
public class ToolMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(ToolMetricsCollector.class);
    private static final int EXPENSIVE_TOKEN_THRESHOLD = 5_000;

    private final ToolExecutionMetricRepository repository;
    private final TokenEstimatorService tokenEstimator;

    public ToolMetricsCollector(ToolExecutionMetricRepository repository,
                                TokenEstimatorService tokenEstimator) {
        this.repository = repository;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * Measures a single tool execution end-to-end.
     *
     * @param toolName  the MCP tool name as registered (e.g. "List-Applications")
     * @param request   the request parameters sent to the tool (used for token estimation)
     * @param execution the actual tool logic to run; exceptions propagate to the caller
     * @param <T>       the tool's return type
     * @return the unmodified value returned by {@code execution}
     */
    public <T> T measure(String toolName, Object request, Supplier<T> execution) {
        int requestTokens = safeCountTokens(request);
        int requestBytes  = safeCountBytes(request);

        long start    = System.currentTimeMillis();
        T    response = execution.get();                          // business exceptions propagate
        long elapsed  = System.currentTimeMillis() - start;

        persistMetrics(toolName, requestTokens, requestBytes, response, elapsed);
        return response;
    }

    // --- private helpers ---

    private void persistMetrics(String toolName, int requestTokens, int requestBytes,
                                Object response, long executionTimeMs) {
        try {
            int responseTokens = safeCountTokens(response);
            int responseBytes  = safeCountBytes(response);
            int totalTokens    = requestTokens + responseTokens;
            boolean expensive  = totalTokens > EXPENSIVE_TOKEN_THRESHOLD;

            if (responseTokens > EXPENSIVE_TOKEN_THRESHOLD) {
                log.warn("[MCP-METRICS] High-token response — tool={} responseTokens={} responseBytes={}",
                        toolName, responseTokens, responseBytes);
            }

            ToolExecutionMetric metric = ToolExecutionMetric.builder()
                    .toolName(toolName)
                    .executionTimeMs(executionTimeMs)
                    .requestBytes(requestBytes)
                    .responseBytes(responseBytes)
                    .requestTokens(requestTokens)
                    .responseTokens(responseTokens)
                    .totalTokens(totalTokens)
                    .expensive(expensive)
                    .createdAt(LocalDateTime.now())
                    .build();

            repository.save(metric);

        } catch (Exception e) {
            log.error("[MCP-METRICS] Failed to persist metrics for tool={}: {}", toolName, e.getMessage());
        }
    }

    private int safeCountTokens(Object obj) {
        try {
            return tokenEstimator.countTokens(obj);
        } catch (Exception e) {
            log.debug("[MCP-METRICS] Token count error: {}", e.getMessage());
            return 0;
        }
    }

    private int safeCountBytes(Object obj) {
        try {
            return tokenEstimator.countBytes(obj);
        } catch (Exception e) {
            log.debug("[MCP-METRICS] Byte count error: {}", e.getMessage());
            return 0;
        }
    }
}
