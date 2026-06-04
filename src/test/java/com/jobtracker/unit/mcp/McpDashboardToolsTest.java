package com.jobtracker.unit.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.dto.gamification.AchievementResponse;
import com.jobtracker.dto.gamification.GamificationProfileResponse;
import com.jobtracker.mcp.resources.McpReadOnlySnapshotResources;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GamificationService;
import com.jobtracker.service.GoogleDriveService;
import com.jobtracker.mapper.AuthMapper;
import com.jobtracker.util.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpDashboardToolsTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private GamificationService gamificationService;

    @Mock
    private GoogleDriveService googleDriveService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private SecurityUtils securityUtils;

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
    void pipelineSummary_serializesDashboardSummary() throws Exception {
        DashboardSummaryResponse expected = new DashboardSummaryResponse(
                25L, 10L, 3L, 7L, 2L, 5L, 4L, 2L, 1L, 1.2d, 6.5d, 18.0d);
        when(dashboardService.getSummary()).thenReturn(expected);

        String result = resources.pipelineSummary();

        assertThat(result).isEqualTo(objectMapper.writeValueAsString(expected));
        verify(dashboardService).getSummary();
    }

    @Test
    void gamificationProfile_serializesGamificationProfile() throws Exception {
        GamificationProfileResponse expected = new GamificationProfileResponse(
                75L, 1, 0L, 100L, 25L, 75, "Desempregado de Aluguel", 4);
        when(gamificationService.getProfile()).thenReturn(expected);

        String result = resources.gamificationProfile();

        assertThat(result).isEqualTo(objectMapper.writeValueAsString(expected));
        verify(gamificationService).getProfile();
    }

    @Test
    void achievements_serializesAchievementList() throws Exception {
        List<AchievementResponse> expected = List.of(
                new AchievementResponse("EARLY_BIRD", "Early Bird", "Apply early", "sunrise", true,
                        LocalDateTime.of(2026, 6, 4, 10, 30))
        );
        when(gamificationService.getAchievements()).thenReturn(expected);

        String result = resources.achievements();

        assertThat(result).isEqualTo(objectMapper.writeValueAsString(expected));
        verify(gamificationService).getAchievements();
    }
}
