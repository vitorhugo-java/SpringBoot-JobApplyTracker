package com.jobtracker.controller;

import com.jobtracker.dto.metrics.AvgExecutionTimeProjection;
import com.jobtracker.dto.metrics.MostExpensiveExecutionResponse;
import com.jobtracker.dto.metrics.ToolUsageByDayProjection;
import com.jobtracker.dto.metrics.TopExpensiveToolProjection;
import com.jobtracker.entity.ToolExecutionMetric;
import com.jobtracker.repository.ToolExecutionMetricRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Tool Metrics", description = "MCP tool usage analytics — token costs, execution times, and call volumes")
@RestController
@RequestMapping("/api/v1/internal/metrics/tools")
public class ToolMetricsController {

    private final ToolExecutionMetricRepository repository;

    public ToolMetricsController(ToolExecutionMetricRepository repository) {
        this.repository = repository;
    }

    @Operation(
            summary = "Top expensive tools",
            description = "Tools ranked by average total tokens (request + response). Identifies habitual heavy callers.")
    @GetMapping("/top-expensive")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TopExpensiveToolProjection>> getTopExpensiveTools() {
        return ResponseEntity.ok(repository.findTopExpensiveTools());
    }

    @Operation(
            summary = "Tool call volume by day",
            description = "Total number of tool invocations per calendar day, most-recent first.")
    @GetMapping("/usage-by-day")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ToolUsageByDayProjection>> getUsageByDay() {
        return ResponseEntity.ok(repository.findUsageByDay());
    }

    @Operation(
            summary = "Most expensive individual executions",
            description = "The N executions with the highest total token count. Default limit is 20.")
    @GetMapping("/most-expensive")
    @Transactional(readOnly = true)
    public ResponseEntity<List<MostExpensiveExecutionResponse>> getMostExpensiveExecutions(
            @RequestParam(defaultValue = "20") int limit) {
        List<ToolExecutionMetric> rows = repository.findMostExpensiveExecutions(Pageable.ofSize(limit));
        List<MostExpensiveExecutionResponse> body = rows.stream()
                .map(m -> new MostExpensiveExecutionResponse(
                        m.getId(),
                        m.getToolName(),
                        m.getExecutionTimeMs(),
                        m.getRequestBytes(),
                        m.getResponseBytes(),
                        m.getRequestTokens(),
                        m.getResponseTokens(),
                        m.getTotalTokens(),
                        m.isExpensive(),
                        m.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(
            summary = "Average execution time per tool",
            description = "Wall-clock average in milliseconds per tool, slowest-first. Useful for latency profiling.")
    @GetMapping("/avg-execution-time")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AvgExecutionTimeProjection>> getAvgExecutionTime() {
        return ResponseEntity.ok(repository.findAvgExecutionTimePerTool());
    }
}
