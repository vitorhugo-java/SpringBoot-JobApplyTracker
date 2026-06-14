package com.jobtracker.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpSchema.Annotations;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Overrides the static {@code lastModified} declared on {@code @McpResource} annotations with the
 * current timestamp, so MCP resources never advertise a stale hard-coded date.
 *
 * <p>{@code lastModified} is an annotation attribute (a compile-time constant), and the framework
 * freezes it into the {@code resources/list} payload at registration via {@code ResourceAdapter}.
 * This post-processor rewrites the registered resource and resource-template specifications,
 * replacing {@code annotations.lastModified} with an ISO-8601 date-time (date <em>and</em> time,
 * since the field is a free-form string). The value is therefore "today" as of server startup /
 * registration — the resources represent live snapshots, so the timestamp reflects when the server
 * last (re)started rather than a fixed past date.
 */
@Component
public class McpResourceLastModifiedPostProcessor implements BeanPostProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof List<?> list) || list.isEmpty()) {
            return bean;
        }
        Object first = list.get(0);
        if (first instanceof SyncResourceSpecification) {
            String timestamp = currentTimestamp();
            List<SyncResourceSpecification> out = new ArrayList<>(list.size());
            for (SyncResourceSpecification spec : (List<SyncResourceSpecification>) list) {
                out.add(withLastModified(spec, timestamp));
            }
            return out;
        }
        if (first instanceof SyncResourceTemplateSpecification) {
            String timestamp = currentTimestamp();
            List<SyncResourceTemplateSpecification> out = new ArrayList<>(list.size());
            for (SyncResourceTemplateSpecification spec : (List<SyncResourceTemplateSpecification>) list) {
                out.add(withLastModified(spec, timestamp));
            }
            return out;
        }
        return bean;
    }

    /** ISO-8601 date-time with offset, e.g. {@code 2026-06-14T14:50:35.123-03:00}. */
    static String currentTimestamp() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static SyncResourceSpecification withLastModified(SyncResourceSpecification spec, String timestamp) {
        Resource r = spec.resource();
        Resource updated = Resource.builder()
                .uri(r.uri())
                .name(r.name())
                .title(r.title())
                .description(r.description())
                .mimeType(r.mimeType())
                .size(r.size())
                .annotations(withLastModified(r.annotations(), timestamp))
                .meta(r.meta())
                .build();
        return new SyncResourceSpecification(updated, spec.readHandler());
    }

    static SyncResourceTemplateSpecification withLastModified(SyncResourceTemplateSpecification spec, String timestamp) {
        ResourceTemplate t = spec.resourceTemplate();
        ResourceTemplate updated = new ResourceTemplate(
                t.uriTemplate(), t.name(), t.title(), t.description(), t.mimeType(),
                withLastModified(t.annotations(), timestamp), t.meta());
        return new SyncResourceTemplateSpecification(updated, spec.readHandler());
    }

    static Annotations withLastModified(Annotations annotations, String timestamp) {
        if (annotations == null) {
            return new Annotations(null, null, timestamp);
        }
        return new Annotations(annotations.audience(), annotations.priority(), timestamp);
    }
}
