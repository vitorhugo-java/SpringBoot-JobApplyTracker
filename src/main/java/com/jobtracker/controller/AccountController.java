package com.jobtracker.controller;

import com.jobtracker.dto.auth.MessageResponse;
import com.jobtracker.entity.User;
import com.jobtracker.service.EmailService;
import com.jobtracker.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "Account configuration endpoints")
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final EmailService emailService;
    private final SecurityUtils securityUtils;

    public AccountController(EmailService emailService, SecurityUtils securityUtils) {
        this.emailService = emailService;
        this.securityUtils = securityUtils;
    }

    @Operation(
        summary = "Send test email",
        description = "Sends a test email to the currently authenticated user's email address",
        responses = {
            @ApiResponse(responseCode = "200", description = "Test email processed",
                content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
        }
    )
    @PostMapping("/test-email")
    public ResponseEntity<MessageResponse> sendTestEmail() {
        User user = securityUtils.getCurrentUser();

        if (!emailService.isMailEnabled()) {
            return ResponseEntity.ok(new MessageResponse("Email sending is disabled in server configuration"));
        }

        emailService.sendTestEmail(user);
        return ResponseEntity.ok(new MessageResponse("Test email sent successfully"));
    }
}
