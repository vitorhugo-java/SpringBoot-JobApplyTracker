package com.jobtracker.controller;

import com.google.gson.Gson;
import com.jobtracker.dto.gpt.GptAuthorizationLoginRequest;
import com.jobtracker.dto.gpt.GptAuthorizationRequest;
import com.jobtracker.dto.gpt.GptTokenRequest;
import com.jobtracker.dto.gpt.GptTokenResponse;
import com.jobtracker.service.GptAuthorizationPageRenderer;
import com.jobtracker.service.GptOAuthAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
        log.info("Rendering GPT Action authorization page for {}", new Gson().toJson(request));
        return ResponseEntity.ok()
                .contentType(pageRenderer.mediaType())
                .body(pageRenderer.render(request, null));
    }

    @PostMapping(value = "/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> authorizeLogin(@Valid GptAuthorizationLoginRequest request) {
        String redirectUri = authorizationService.authorize(request);
        log.info("User authorized GPT Action access for object: {}, redirecting to {}", new Gson().toJson(request), redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri)
                .build();
    }

    @Operation(summary = "Exchange an authorization code for a GPT Action access token")
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public ResponseEntity<GptTokenResponse> token(@Valid GptTokenRequest request,
                                                  @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                                  String authorizationHeader) {
        GptTokenResponse response = authorizationService.exchangeToken(request, authorizationHeader);
        log.info("Issued GPT Action access token for object: {}", new Gson().toJson(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

}
