package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.McpResourceLastModifiedPostProcessor;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.Annotations;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class McpResourceLastModifiedPostProcessorTest {

    private final McpResourceLastModifiedPostProcessor processor = new McpResourceLastModifiedPostProcessor();

    private static final BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> HANDLER =
            (exchange, request) -> new ReadResourceResult(List.of());

    @Test
    @SuppressWarnings("unchecked")
    void rewritesResourceLastModifiedToTodayWithTime_preservingAudiencePriorityAndHandler() {
        Resource resource = Resource.builder()
                .uri("resource://job-apply-tracker/pipeline-summary")
                .name("Pipeline Summary")
                .mimeType("application/json")
                .annotations(new Annotations(List.of(Role.USER, Role.ASSISTANT), 1.0d, "2026-06-04"))
                .build();
        SyncResourceSpecification spec = new SyncResourceSpecification(resource, HANDLER);

        List<SyncResourceSpecification> result =
                (List<SyncResourceSpecification>) processor.postProcessAfterInitialization(List.of(spec), "resourceSpecs");

        Annotations updated = result.get(0).resource().annotations();
        assertThat(updated.lastModified()).startsWith(LocalDate.now().toString());
        assertThat(updated.lastModified()).contains("T"); // includes time component
        assertThat(updated.lastModified()).isNotEqualTo("2026-06-04");
        assertThat(updated.audience()).containsExactly(Role.USER, Role.ASSISTANT);
        assertThat(updated.priority()).isEqualTo(1.0d);
        assertThat(result.get(0).readHandler()).isSameAs(HANDLER);
        assertThat(result.get(0).resource().name()).isEqualTo("Pipeline Summary");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rewritesResourceTemplateLastModified() {
        ResourceTemplate template = new ResourceTemplate(
                "resource://job-apply-tracker/base-resume/{resumeId}",
                "Base Resume Content", "Base Resume Content", "desc", "text/plain",
                new Annotations(List.of(Role.USER), 0.8d, "2026-06-04"));
        SyncResourceTemplateSpecification spec = new SyncResourceTemplateSpecification(template, HANDLER);

        List<SyncResourceTemplateSpecification> result =
                (List<SyncResourceTemplateSpecification>) processor.postProcessAfterInitialization(List.of(spec), "resourceTemplateSpecs");

        Annotations updated = result.get(0).resourceTemplate().annotations();
        assertThat(updated.lastModified()).startsWith(LocalDate.now().toString());
        assertThat(updated.lastModified()).contains("T");
        assertThat(result.get(0).resourceTemplate().uriTemplate())
                .isEqualTo("resource://job-apply-tracker/base-resume/{resumeId}");
        assertThat(result.get(0).readHandler()).isSameAs(HANDLER);
    }

    @Test
    void ignoresUnrelatedBeans() {
        Object bean = List.of("not", "a", "spec");
        assertThat(processor.postProcessAfterInitialization(bean, "someList")).isSameAs(bean);

        Object other = "plain-string";
        assertThat(processor.postProcessAfterInitialization(other, "x")).isSameAs(other);
    }
}
