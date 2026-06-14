package com.jobtracker.unit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.mcp.audit.AuditMcpOperation;
import com.jobtracker.mcp.audit.McpAuditAspect;
import com.jobtracker.service.McpAuditService;
import com.jobtracker.service.TokenEstimatorService;
import com.jobtracker.util.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.modelcontextprotocol.spec.McpSchema;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.context.McpSyncRequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpAuditAspect}. The aspect is exercised directly (not via an AOP proxy)
 * with a real {@link McpAuditService} backed by a {@link SimpleMeterRegistry}, so the assertions
 * verify the end-to-end behavior: result passthrough, exception rethrow, and the emitted meters.
 */
class McpAuditAspectTest {

    private MeterRegistry meterRegistry;
    private McpAuditAspect aspect;
    private Span span;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUser()).thenThrow(new RuntimeException("no auth in unit test"));
        TokenEstimatorService tokenEstimator = new TokenEstimatorService(new ObjectMapper());
        McpAuditService auditService = new McpAuditService(meterRegistry, tokenEstimator, securityUtils);

        // Tracer is a no-op stub: every fluent call returns the same span.
        Tracer tracer = mock(Tracer.class);
        span = mock(Span.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(span.error(any())).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(mock(Tracer.SpanInScope.class));

        aspect = new McpAuditAspect(auditService, tracer);
    }

    @Test
    void successfulExecution_returnsResultAndRecordsSuccessMeter() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("createApplication",
                new String[] {"ctx", "vacancyName"}, new Object[] {null, "Backend Engineer"});
        when(jp.proceed()).thenReturn("RESULT");

        Object result = aspect.audit(jp, annotation("Create-Application", ""));

        assertThat(result).isEqualTo("RESULT");
        assertThat(invocationCount("Create-Application", "SUCCESS")).isEqualTo(1.0);
    }

    @Test
    void exception_recordsErrorMeterAndRethrows() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("deleteApplication",
                new String[] {"ctx", "id"}, new Object[] {null, "abc"});
        when(jp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.audit(jp, annotation("Delete-Application", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(invocationCount("Delete-Application", "ERROR")).isEqualTo(1.0);
    }

    @Test
    void providerResolvedFromAnnotationAttribute() throws Throwable {
        ProceedingJoinPoint jp = joinPoint("getAnalytics",
                new String[] {"ctx"}, new Object[] {null});
        when(jp.proceed()).thenReturn("ok");

        aspect.audit(jp, annotation("Get-Analytics", "CHATGPT"));

        assertThat(meterRegistry.get("mcp.tool.invocations")
                .tag("action", "Get-Analytics")
                .tag("provider", "CHATGPT")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void providerResolvedFromMcpRequestContextClientInfo() throws Throwable {
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);
        when(ctx.clientInfo()).thenReturn(new McpSchema.Implementation("claude-code", "Claude Code", "2.1.177"));

        ProceedingJoinPoint jp = joinPoint("listStatuses",
                new String[] {"ctx"}, new Object[] {ctx});
        when(jp.proceed()).thenReturn("ok");

        aspect.audit(jp, annotation("List-Statuses", ""));

        assertThat(meterRegistry.get("mcp.tool.invocations")
                .tag("action", "List-Statuses")
                .tag("provider", "CLAUDE")
                .counter().count()).isEqualTo(1.0);
    }

    // --- helpers ---

    private double invocationCount(String action, String status) {
        return meterRegistry.get("mcp.tool.invocations")
                .tag("action", action)
                .tag("status", status)
                .counter().count();
    }

    private static ProceedingJoinPoint joinPoint(String method, String[] paramNames, Object[] args) {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(jp.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(paramNames);
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    private static AuditMcpOperation annotation(String action, String provider) {
        AuditMcpOperation ann = mock(AuditMcpOperation.class);
        when(ann.action()).thenReturn(action);
        when(ann.provider()).thenReturn(provider);
        return ann;
    }
}
