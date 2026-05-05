package com.jobtracker.controller;

import com.jobtracker.ApiVersion;
import com.jobtracker.dto.gamification.AchievementResponse;
import com.jobtracker.dto.gamification.GamificationEventRequest;
import com.jobtracker.dto.gamification.GamificationEventResponse;
import com.jobtracker.dto.gamification.GamificationProfileResponse;
import com.jobtracker.service.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Gamification", description = "Gamification profile, achievements and XP event endpoints")
@RestController
@RequestMapping(ApiVersion.V1 + "/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @Operation(
            summary = "Get current gamification profile",
            description = "Returns the authenticated user's current XP snapshot, level progress and rank title",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Gamification profile",
                            content = @Content(schema = @Schema(implementation = GamificationProfileResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Not authenticated")
            }
    )
    @GetMapping("/profile")
    public ResponseEntity<GamificationProfileResponse> getProfile() {
        return ResponseEntity.ok(gamificationService.getProfile());
    }

    @Operation(
            summary = "List achievements",
            description = "Returns the authenticated user's achievement catalog and unlocked state",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Achievement catalog",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AchievementResponse.class)))),
                    @ApiResponse(responseCode = "401", description = "Not authenticated")
            }
    )
    @GetMapping("/achievements")
    public ResponseEntity<List<AchievementResponse>> getAchievements() {
        return ResponseEntity.ok(gamificationService.getAchievements());
    }

    @Operation(
            summary = "Apply a gamification event",
            description = "Registers a tracked client event and returns the updated XP snapshot",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Event applied",
                            content = @Content(schema = @Schema(implementation = GamificationEventResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Validation error"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated")
            }
    )
    @PostMapping("/events")
    public ResponseEntity<GamificationEventResponse> applyEvent(@Valid @RequestBody GamificationEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gamificationService.applyEvent(request));
    }
}
