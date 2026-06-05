package com.jobtracker.mcp;

import org.springframework.context.annotation.Configuration;

/**
 * Base configuration anchor for MCP tool beans.
 * Tools are registered via @McpTool + @Component (McpApplicationTools, McpGoogleDriveTools, McpAnalyticsTools).
 */
@Configuration
public class McpToolsConfig {
}
