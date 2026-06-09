package com.jobtracker.unit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.auth.UserResponse;
import com.jobtracker.entity.User;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.mcp.resources.McpReadOnlySnapshotResources;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GamificationService;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpProfileToolsTest {

    @Mock
    private AuthMapper authMapper;

    @Mock
    private SecurityUtils securityUtils;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private GamificationService gamificationService;

    @Mock
    private GoogleDriveService googleDriveService;

    private ObjectMapper objectMapper;
    private McpReadOnlySnapshotResources resources;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        resources = new McpReadOnlySnapshotResources(
                dashboardService,
                gamificationService,
                googleDriveService,
                authMapper,
                securityUtils,
                objectMapper);
    }

    @Test
    void currentUser_serializesAuthenticatedUser() throws Exception {
        User user = mock(User.class);
        UserResponse expected = new UserResponse(
                java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "John Doe",
                "john@example.com",
                java.time.LocalTime.of(19, 0),
                java.util.Set.of("USER", "BETA"),
                true, true);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(expected);

        String result = resources.currentUser();

        assertThat(result).isEqualTo(objectMapper.writeValueAsString(expected));
        verify(securityUtils).getCurrentUser();
        verify(authMapper).toUserResponse(user);
    }
}
