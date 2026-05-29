package com.jobtracker.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.gpt.GptAuthorizationLoginRequest;
import com.jobtracker.dto.gpt.GptAuthorizationRequest;
import com.jobtracker.dto.gpt.GptTokenRequest;
import com.jobtracker.dto.gpt.GptTokenResponse;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.UnauthorizedException;
import com.jobtracker.service.GptAuthorizationPageRenderer;
import com.jobtracker.service.GptOAuthAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    private final GptOAuthAuthorizationService authorizationService;
    private final GptAuthorizationPageRenderer pageRenderer;
    private final ObjectMapper objectMapper;

    public GptOAuthController(GptOAuthAuthorizationService authorizationService,
                              GptAuthorizationPageRenderer pageRenderer,
                              ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.pageRenderer = pageRenderer;
        this.objectMapper = objectMapper;
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

    @Operation(summary = "Exchange an authorization code for a GPT Action access token")
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseBody
    public ResponseEntity<GptTokenResponse> token(@Valid GptTokenRequest request,
                                                  @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false)
                                                  String authorizationHeader) {
        GptTokenResponse response = authorizationService.exchangeToken(request, authorizationHeader);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(response);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({BadRequestException.class, UnauthorizedException.class})
    @ResponseBody
    public ResponseEntity<String> handleAuthorizationError(Exception ex, jakarta.servlet.http.HttpServletRequest servletRequest) {
        String responseType = servletRequest.getParameter("response_type");
        String clientId = servletRequest.getParameter("client_id");
        String redirectUri = servletRequest.getParameter("redirect_uri");
        String scope = servletRequest.getParameter("scope");
        String state = servletRequest.getParameter("state");
        String codeChallenge = servletRequest.getParameter("code_challenge");
        String codeChallengeMethod = servletRequest.getParameter("code_challenge_method");

        if ("/authorize".equals(servletRequest.getServletPath()) && responseType != null && clientId != null
                && redirectUri != null && codeChallenge != null && codeChallengeMethod != null) {
            GptAuthorizationRequest request = new GptAuthorizationRequest(
                    responseType,
                    clientId,
                    redirectUri,
                    scope,
                    state,
                    codeChallenge,
                    codeChallengeMethod
            );
            return ResponseEntity.status(ex instanceof UnauthorizedException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                    .contentType(pageRenderer.mediaType())
                    .body(pageRenderer.render(request, ex.getMessage()));
        }

        return ResponseEntity.status(ex instanceof UnauthorizedException ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJsonError(ex.getMessage()));
    }

    private String toJsonError(String message) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of("message", message));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize OAuth error response", ex);
        }
    }
}
