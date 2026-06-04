package com.jobtracker.mcp.tools;

import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.util.SecurityUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class McpProfileTools {

    private final AuthMapper authMapper;
    private final SecurityUtils securityUtils;

    public McpProfileTools(AuthMapper authMapper, SecurityUtils securityUtils) {
        this.authMapper = authMapper;
        this.securityUtils = securityUtils;
    }

    @Tool(description = """
            Get the authenticated user's profile: id, name, email, preferred daily reminder time,
            granted roles, and whether Google Drive integration features are available.
            """,
            name = "Get Current User Profile")
    public UserResponse getCurrentUser() {
        return authMapper.toUserResponse(securityUtils.getCurrentUser());
    }
}
