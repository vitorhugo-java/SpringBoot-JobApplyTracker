package com.jobtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_execution_metrics", indexes = {
        @Index(name = "idx_tool_metrics_tool_name", columnList = "tool_name"),
        @Index(name = "idx_tool_metrics_created_at", columnList = "created_at"),
        @Index(name = "idx_tool_metrics_expensive", columnList = "expensive")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionMetric {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tool_name", nullable = false, length = 255)
    private String toolName;

    @Column(name = "execution_time_ms", nullable = false)
    private long executionTimeMs;

    @Column(name = "request_bytes", nullable = false)
    private int requestBytes;

    @Column(name = "response_bytes", nullable = false)
    private int responseBytes;

    @Column(name = "request_tokens", nullable = false)
    private int requestTokens;

    @Column(name = "response_tokens", nullable = false)
    private int responseTokens;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "expensive", nullable = false)
    private boolean expensive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
