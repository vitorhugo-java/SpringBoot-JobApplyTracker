package com.jobtracker.mcp.tools;

import com.jobtracker.dto.dashboard.DashboardSummaryResponse;
import com.jobtracker.dto.gamification.AchievementResponse;
import com.jobtracker.dto.gamification.GamificationProfileResponse;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.GamificationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpDashboardTools {

    private final DashboardService dashboardService;
    private final GamificationService gamificationService;

    public McpDashboardTools(DashboardService dashboardService, GamificationService gamificationService) {
        this.dashboardService = dashboardService;
        this.gamificationService = gamificationService;
    }

    @Tool(description = """
            Get pipeline summary statistics for the authenticated user:
            total applications, by-status breakdown, interview counts,
            overdue follow-ups, daily/weekly/monthly application rates.
            """, name = "Get Pipeline Summary")
    public DashboardSummaryResponse getPipelineSummary() {
        return dashboardService.getSummary();
    }

    @Tool(description = "Get the authenticated user's gamification profile: XP, level, rank title, streak days.", name = "Get Gamification Profile")
    public GamificationProfileResponse getGamificationProfile() {
        return gamificationService.getProfile();
    }

    @Tool(description = "List all gamification achievements with unlock status and timestamp.", name = "Get Achievements")
    public List<AchievementResponse> getAchievements() {
        return gamificationService.getAchievements();
    }
}
