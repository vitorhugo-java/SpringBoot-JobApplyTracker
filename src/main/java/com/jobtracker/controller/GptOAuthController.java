package com.jobtracker.controller;

import com.jobtracker.dto.gpt.GptAuthorizationLoginRequest;
import com.jobtracker.dto.gpt.GptAuthorizationRequest;
import com.jobtracker.dto.gpt.GptTokenRequest;
import com.jobtracker.dto.gpt.GptTokenResponse;
import com.jobtracker.service.GptAuthorizationPageRenderer;
import com.jobtracker.service.GptOAuthAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/oauth2")
@Tag(name = "GPT OAuth", description = "OAuth 2.0 Authorization Code with PKCE endpoints for GPT Actions")
public class GptOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GptOAuthController.class);
    private final GptOAuthAuthorizationService authorizationService;
    private final GptAuthorizationPageRenderer pageRenderer;

    public GptOAuthController(GptOAuthAuthorizationService authorizationService,
                              GptAuthorizationPageRenderer pageRenderer) {
        this.authorizationService = authorizationService;
        this.pageRenderer = pageRenderer;
    }

    @Operation(summary = "Render GPT Action authorization page")
    @GetMapping(value = "/authorize", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> authorize(@Valid GptAuthorizationRequest request) {
        authorizationService.validateAuthorizationRequest(request);
        return ResponseEntity.ok()
                .contentType(pageRenderer.mediaType())
                .body(pageRenderer.render(request, null));
    }

    @PostMapping(value = "/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> authorizeLogin(@Valid GptAuthorizationLoginRequest request) {
        String redirectUri = authorizationService.authorize(request);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri)
                .build();
    }

    @PostMapping(
            value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<GptTokenResponse> token(
            @ModelAttribute @Valid GptTokenRequest request,
            @Parameter(hidden = true) @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {

        GptTokenResponse response = authorizationService.exchangeToken(request, authorizationHeader);
        log.info("Issued GPT Action access token for access_token={}, scope={}", response.access_token(), response.scope());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }
}
