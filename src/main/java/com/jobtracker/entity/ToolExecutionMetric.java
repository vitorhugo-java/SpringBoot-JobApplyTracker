package com.jobtracker.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_execution_metrics", indexes = {
        @Index(name = "idx_tool_metrics_tool_name", columnList = "tool_name"),
        @Index(name = "idx_tool_metrics_created_at", columnList = "created_at"),
        @Index(name = "idx_tool_metrics_expensive", columnList = "expensive")
})
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public int getRequestBytes() { return requestBytes; }
    public void setRequestBytes(int requestBytes) { this.requestBytes = requestBytes; }

    public int getResponseBytes() { return responseBytes; }
    public void setResponseBytes(int responseBytes) { this.responseBytes = responseBytes; }

    public int getRequestTokens() { return requestTokens; }
    public void setRequestTokens(int requestTokens) { this.requestTokens = requestTokens; }

    public int getResponseTokens() { return responseTokens; }
    public void setResponseTokens(int responseTokens) { this.responseTokens = responseTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public boolean isExpensive() { return expensive; }
    public void setExpensive(boolean expensive) { this.expensive = expensive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String toolName;
        private long executionTimeMs;
        private int requestBytes;
        private int responseBytes;
        private int requestTokens;
        private int responseTokens;
        private int totalTokens;
        private boolean expensive;
        private LocalDateTime createdAt;

        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder executionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; return this; }
        public Builder requestBytes(int requestBytes) { this.requestBytes = requestBytes; return this; }
        public Builder responseBytes(int responseBytes) { this.responseBytes = responseBytes; return this; }
        public Builder requestTokens(int requestTokens) { this.requestTokens = requestTokens; return this; }
        public Builder responseTokens(int responseTokens) { this.responseTokens = responseTokens; return this; }
        public Builder totalTokens(int totalTokens) { this.totalTokens = totalTokens; return this; }
        public Builder expensive(boolean expensive) { this.expensive = expensive; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public ToolExecutionMetric build() {
            ToolExecutionMetric m = new ToolExecutionMetric();
            m.toolName = toolName;
            m.executionTimeMs = executionTimeMs;
            m.requestBytes = requestBytes;
            m.responseBytes = responseBytes;
            m.requestTokens = requestTokens;
            m.responseTokens = responseTokens;
            m.totalTokens = totalTokens;
            m.expensive = expensive;
            m.createdAt = createdAt;
            return m;
        }
    }
}
