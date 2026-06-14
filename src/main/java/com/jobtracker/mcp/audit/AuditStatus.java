package com.jobtracker.mcp.audit;

/** Execution outcome of an audited MCP operation; used as a bounded Prometheus tag. */
public enum AuditStatus {
    SUCCESS,
    ERROR
}
