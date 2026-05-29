package com.jobtracker.service;

import com.jobtracker.config.GptOAuthProperties;
import com.jobtracker.dto.gpt.GptAuthorizationLoginRequest;
import com.jobtracker.dto.gpt.GptAuthorizationRequest;
import com.jobtracker.dto.gpt.GptTokenRequest;
import com.jobtracker.dto.gpt.GptTokenResponse;
import com.jobtracker.entity.GptOAuthAuthorizationCode;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.UnauthorizedException;
import com.jobtracker.repository.GptOAuthAuthorizationCodeRepository;
import com.jobtracker.repository.UserRepository;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GptOAuthAuthorizationService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final GptOAuthAuthorizationCodeRepository authorizationCodeRepository;
    private final GptOAuthClientService clientService;
    private final GptOAuthTokenService tokenService;
    private final GptOAuthProperties properties;

    public GptOAuthAuthorizationService(AuthenticationManager authenticationManager,
                                        UserRepository userRepository,
                                        GptOAuthAuthorizationCodeRepository authorizationCodeRepository,
                                        GptOAuthClientService clientService,
                                        GptOAuthTokenService tokenService,
                                        GptOAuthProperties properties) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.authorizationCodeRepository = authorizationCodeRepository;
        this.clientService = clientService;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public GptOAuthClientService.ValidatedAuthorizationRequest validateAuthorizationRequest(GptAuthorizationRequest request) {
        return clientService.validateAuthorizationRequest(request);
    }

    @Transactional
    public String authorize(GptAuthorizationLoginRequest request) {
        GptOAuthClientService.ValidatedAuthorizationRequest validated = clientService.validateAuthorizationRequest(
                new GptAuthorizationRequest(
                        request.response_type(),
                        request.client_id(),
                        request.redirect_uri(),
                        request.scope(),
                        request.state(),
                        request.code_challenge(),
                        request.code_challenge_method()
                )
        );

        if (!request.approved()) {
            return buildRedirect(validated.redirectUri(), validated.state(), null, "access_denied", "User denied access");
        }

        User user = authenticateUser(request.email(), request.password());
        authorizationCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        String code = generateCode();
        GptOAuthAuthorizationCode authorizationCode = new GptOAuthAuthorizationCode();
        authorizationCode.setUser(user);
        authorizationCode.setClientId(validated.clientId());
        authorizationCode.setRedirectUri(validated.redirectUri());
        authorizationCode.setScopeValue(validated.scopeValue());
        authorizationCode.setCodeHash(hash(code));
        authorizationCode.setCodeChallenge(validated.codeChallenge());
        authorizationCode.setCodeChallengeMethod(validated.codeChallengeMethod());
        authorizationCode.setExpiresAt(LocalDateTime.now().plusSeconds(properties.getAuthorizationCodeExpirationSeconds()));
        authorizationCodeRepository.save(authorizationCode);

        return buildRedirect(validated.redirectUri(), validated.state(), code, null, null);
    }

    @Transactional
    public GptTokenResponse exchangeToken(GptTokenRequest request, String authorizationHeader) {
        clientService.validateClientAuthentication(request, authorizationHeader);
        if (!"authorization_code".equals(request.grant_type())) {
            throw new BadRequestException("Unsupported grant_type");
        }

        authorizationCodeRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        GptOAuthAuthorizationCode authorizationCode = authorizationCodeRepository.findByCodeHash(hash(request.code()))
                .orElseThrow(() -> new UnauthorizedException("Invalid authorization code"));

        if (authorizationCode.getUsedAt() != null) {
            throw new UnauthorizedException("Authorization code has already been used");
        }
        if (authorizationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Authorization code has expired");
        }
        if (!authorizationCode.getRedirectUri().equals(request.redirect_uri())) {
            throw new UnauthorizedException("redirect_uri does not match authorization code");
        }
        if (!authorizationCode.getClientId().equals(properties.getClientId())) {
            throw new UnauthorizedException("Authorization code does not belong to the configured client");
        }
        if (!verifyPkce(request.code_verifier(), authorizationCode.getCodeChallenge(), authorizationCode.getCodeChallengeMethod())) {
            throw new UnauthorizedException("Invalid code_verifier");
        }

        authorizationCode.setUsedAt(LocalDateTime.now());
        authorizationCodeRepository.save(authorizationCode);

        Set<String> scopes = Arrays.stream(authorizationCode.getScopeValue().split(GptOAuthClientService.SCOPE_DELIMITER))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        GptOAuthTokenService.IssuedAccessToken issuedToken = tokenService.issueAccessToken(authorizationCode.getUser(), scopes);
        return new GptTokenResponse(
                issuedToken.tokenValue(),
                "Bearer",
                issuedToken.expiresIn(),
                issuedToken.scopeValue()
        );
    }

    private User authenticateUser(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
    }

    private boolean verifyPkce(String verifier, String expectedChallenge, String method) {
        if (!"S256".equals(method)) {
            return false;
        }
        byte[] hashedVerifier = DigestUtils.sha256(verifier.getBytes(StandardCharsets.US_ASCII));
        String actualChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hashedVerifier);
        return actualChallenge.equals(expectedChallenge);
    }

    private String buildRedirect(String redirectUri, String state, String code, String error, String errorDescription) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri);
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }
        if (code != null) {
            builder.queryParam("code", code);
        }
        if (error != null) {
            builder.queryParam("error", error);
        }
        if (errorDescription != null) {
            builder.queryParam("error_description", errorDescription);
        }
        return builder.build(true).toUriString();
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        SecureRandomHolder.INSTANCE.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        return DigestUtils.sha256Hex(value);
    }

    private static final class SecureRandomHolder {
        private static final SecureRandom INSTANCE = new SecureRandom();

        private SecureRandomHolder() {
        }
    }
}
