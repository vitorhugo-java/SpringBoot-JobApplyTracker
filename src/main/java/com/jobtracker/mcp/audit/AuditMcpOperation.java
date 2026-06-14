package com.jobtracker.mcp.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an MCP tool method for automatic auditing by {@code McpAuditAspect}.
 *
 * <p>Every invocation of an annotated method emits Micrometer meters
 * ({@code mcp.tool.invocations}, {@code mcp.tool.duration}, {@code mcp.tool.tokens.*})
 * tagged with the action, the resolved MCP provider, and the execution status, plus a
 * structured DEBUG audit log line. Nothing is persisted to the database.
 *
 * <p>Usage:
 * <pre>{@code
 * @AuditMcpOperation(action = "Create-Application")
 * public ApplicationResponse createApplication(McpSyncRequestContext ctx, ...) { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditMcpOperation {

    /**
     * The audited action name (typically the MCP tool name, e.g. {@code "Create-Application"}).
     */
    String action();

    /**
     * Optional explicit provider override. When blank (the default), the provider is resolved
     * from the method parameters and then from the MCP request context ({@code clientInfo()}).
     */
    String provider() default "";
}
