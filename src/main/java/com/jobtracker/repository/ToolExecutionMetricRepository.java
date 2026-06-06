package com.jobtracker.repository;

import com.jobtracker.dto.metrics.AvgExecutionTimeProjection;
import com.jobtracker.dto.metrics.ToolUsageByDayProjection;
import com.jobtracker.dto.metrics.TopExpensiveToolProjection;
import com.jobtracker.entity.ToolExecutionMetric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ToolExecutionMetricRepository extends JpaRepository<ToolExecutionMetric, UUID> {

    /**
     * Top tools ranked by average total tokens (identifies habitually expensive callers).
     */
    @Query("""
            SELECT m.toolName AS toolName,
                   COUNT(m)            AS calls,
                   AVG(m.totalTokens)  AS avgTokens,
                   MAX(m.totalTokens)  AS maxTokens
            FROM ToolExecutionMetric m
            GROUP BY m.toolName
            ORDER BY avgTokens DESC
            """)
    List<TopExpensiveToolProjection> findTopExpensiveTools();

    /**
     * Daily call volume — native SQL so DATE() is resolved by the DB engine.
     */
    @Query(value = """
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day,
                   COUNT(*)                             AS calls
            FROM tool_execution_metrics
            GROUP BY DATE(created_at)
            ORDER BY day DESC
            """, nativeQuery = true)
    List<ToolUsageByDayProjection> findUsageByDay();

    /**
     * Most token-heavy individual executions, useful for spotting outliers.
     */
    @Query("SELECT m FROM ToolExecutionMetric m ORDER BY m.totalTokens DESC")
    List<ToolExecutionMetric> findMostExpensiveExecutions(Pageable pageable);

    /**
     * Average wall-clock execution time per tool, ordered slowest-first.
     */
    @Query("""
            SELECT m.toolName            AS toolName,
                   AVG(m.executionTimeMs) AS avgExecutionTimeMs,
                   COUNT(m)               AS calls
            FROM ToolExecutionMetric m
            GROUP BY m.toolName
            ORDER BY avgExecutionTimeMs DESC
            """)
    List<AvgExecutionTimeProjection> findAvgExecutionTimePerTool();
}
