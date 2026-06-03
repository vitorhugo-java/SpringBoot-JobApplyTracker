package com.jobtracker.unit.mcp;

import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.dto.gamification.AchievementResponse;
import com.jobtracker.dto.gamification.GamificationProfileResponse;
import com.jobtracker.mcp.tools.McpDashboardTools;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GamificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpDashboardToolsTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private GamificationService gamificationService;

    @InjectMocks
    private McpDashboardTools tools;

    @Test
    void getPipelineSummary_delegatesToDashboardService() {
        DashboardSummaryResponse expected = mock(DashboardSummaryResponse.class);
        when(dashboardService.getSummary()).thenReturn(expected);

        DashboardSummaryResponse result = tools.getPipelineSummary();

        assertThat(result).isSameAs(expected);
        verify(dashboardService).getSummary();
    }

    @Test
    void getGamificationProfile_delegatesToGamificationService() {
        GamificationProfileResponse expected = mock(GamificationProfileResponse.class);
        when(gamificationService.getProfile()).thenReturn(expected);

        GamificationProfileResponse result = tools.getGamificationProfile();

        assertThat(result).isSameAs(expected);
        verify(gamificationService).getProfile();
    }

    @Test
    void getAchievements_delegatesToGamificationService() {
        List<AchievementResponse> expected = List.of(mock(AchievementResponse.class));
        when(gamificationService.getAchievements()).thenReturn(expected);

        List<AchievementResponse> result = tools.getAchievements();

        assertThat(result).isSameAs(expected);
        verify(gamificationService).getAchievements();
    }
}
