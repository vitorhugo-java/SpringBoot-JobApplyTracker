package com.jobtracker.mcp;

import com.jobtracker.mcp.tools.McpApplicationTools;
import com.jobtracker.mcp.tools.McpDashboardTools;
import com.jobtracker.mcp.tools.McpGoogleDriveTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    /**
     * Registers all MCP tool objects with Spring AI. The auto-configuration for
     * spring-ai-starter-mcp-server-webmvc collects ToolCallbackProvider beans and exposes
     * their @Tool-annotated methods via the MCP tools/list and tools/call protocol endpoints.
     */
    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            McpApplicationTools applicationTools,
            McpDashboardTools dashboardTools,
            McpGoogleDriveTools driveTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(applicationTools, dashboardTools, driveTools)
                .build();
    }
}
