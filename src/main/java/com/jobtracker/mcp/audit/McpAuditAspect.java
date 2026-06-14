package com.jobtracker.mcp.audit;

import com.jobtracker.service.McpAuditService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.context.McpSyncRequestContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Audits every MCP tool method annotated with {@link AuditMcpOperation}.
 *
 * <p>Cross-cutting concern only: it resolves the audit context (action, provider, request
 * payload), times the call inside a tracing span, and delegates metric/log emission to
 * {@link McpAuditService}. It records SUCCESS on normal return and ERROR on any throwable —
 * then rethrows so business behavior is unchanged.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // outermost: audit wraps security/transaction so denials & failures are captured
public class McpAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(McpAuditAspect.class);

    private final McpAuditService auditService;
    private final Tracer tracer;

    public McpAuditAspect(McpAuditService auditService, Tracer tracer) {
        this.auditService = auditService;
        this.tracer = tracer;
    }

    @Around("@annotation(auditMcpOperation)")
    public Object audit(ProceedingJoinPoint joinPoint, AuditMcpOperation auditMcpOperation) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String action = auditMcpOperation.action();
        String methodName = signature.getName();

        ClientIdentity client = resolveClientIdentity(joinPoint.getArgs());
        McpProvider provider = resolveProvider(auditMcpOperation, signature, joinPoint.getArgs(), client);
        Object requestPayload = buildPayload(signature, joinPoint.getArgs());

        log.debug("[MCP-AUDIT] starting action={} provider={} method={}", action, provider, methodName);

        Span span = tracer.nextSpan().name("mcp.tool." + action).start();
        span.tag("mcp.tool.action", action);
        span.tag("mcp.tool.provider", provider.name());

        long start = System.currentTimeMillis();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            Object response = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            auditService.record(new McpAuditEvent(action, provider, client.name(), client.version(),
                    methodName, requestPayload, response, AuditStatus.SUCCESS, null, elapsed));
            return response;
        } catch (Throwable t) {
            long elapsed = System.currentTimeMillis() - start;
            span.error(t);
            auditService.record(new McpAuditEvent(action, provider, client.name(), client.version(),
                    methodName, requestPayload, null, AuditStatus.ERROR, t.getMessage(), elapsed));
            throw t;
        } finally {
            span.end();
        }
    }

    /** Resolves the provider following: annotation override → method "provider" arg → clientInfo → UNKNOWN. */
    private McpProvider resolveProvider(AuditMcpOperation annotation, MethodSignature signature,
                                        Object[] args, ClientIdentity client) {
        if (annotation.provider() != null && !annotation.provider().isBlank()) {
            return McpProvider.fromClientName(annotation.provider());
        }
        String paramProvider = findProviderParameter(signature, args);
        if (paramProvider != null && !paramProvider.isBlank()) {
            return McpProvider.fromClientName(paramProvider);
        }
        return McpProvider.fromClientName(client.name());
    }

    /**
     * Extracts the MCP client identity from whichever framework context the method declares:
     * {@link McpSyncRequestContext} (tools) or {@link McpSyncServerExchange} (resources). Both
     * expose the {@code clientInfo()} captured during the MCP initialize handshake.
     */
    private static ClientIdentity resolveClientIdentity(Object[] args) {
        if (args == null) {
            return ClientIdentity.UNKNOWN;
        }
        for (Object arg : args) {
            McpSchema.Implementation info = clientInfoOf(arg);
            if (info != null) {
                return new ClientIdentity(info.name(), info.version());
            }
        }
        return ClientIdentity.UNKNOWN;
    }

    private static McpSchema.Implementation clientInfoOf(Object arg) {
        try {
            if (arg instanceof McpSyncRequestContext ctx) {
                return ctx.clientInfo();
            }
            if (arg instanceof McpSyncServerExchange exchange) {
                return exchange.getClientInfo();
            }
        } catch (Exception e) {
            // clientInfo() may be unavailable outside an active MCP request — degrade gracefully.
            return null;
        }
        return null;
    }

    private static boolean isFrameworkParam(Object arg) {
        return arg instanceof McpSyncRequestContext || arg instanceof McpSyncServerExchange;
    }

    private static String findProviderParameter(MethodSignature signature, Object[] args) {
        String[] names = signature.getParameterNames();
        if (names == null || args == null) {
            return null;
        }
        for (int i = 0; i < names.length && i < args.length; i++) {
            if ("provider".equalsIgnoreCase(names[i]) && args[i] instanceof String s) {
                return s;
            }
        }
        return null;
    }

    /** Builds a null-safe {paramName → value} map for token/byte sizing, excluding framework params. */
    private static Object buildPayload(MethodSignature signature, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String[] names = signature.getParameterNames();
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg == null || isFrameworkParam(arg)) {
                continue;
            }
            String key = (names != null && i < names.length) ? names[i] : ("arg" + i);
            payload.put(key, arg);
        }
        return payload.isEmpty() ? null : payload;
    }

    /** Raw MCP client identity captured from the handshake; {@code name}/{@code version} may be null. */
    private record ClientIdentity(String name, String version) {
        static final ClientIdentity UNKNOWN = new ClientIdentity(null, null);
    }
}
