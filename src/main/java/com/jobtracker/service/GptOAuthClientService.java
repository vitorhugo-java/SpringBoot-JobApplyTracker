package com.jobtracker.service;

import com.jobtracker.config.GptOAuthProperties;
import com.jobtracker.dto.gpt.GptAuthorizationRequest;
import com.jobtracker.dto.gpt.GptTokenRequest;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class GptOAuthClientService {
    public static final String SCOPE_DELIMITER = " ";

    private final GptOAuthProperties properties;

    public GptOAuthClientService(GptOAuthProperties properties) {
        this.properties = properties;
    }

    public ValidatedAuthorizationRequest validateAuthorizationRequest(GptAuthorizationRequest request) {
        properties.validateConfigured();

        if (!"code".equals(request.response_type())) {
            throw new BadRequestException("Unsupported response_type");
        }
        if (!properties.getClientId().equals(request.client_id())) {
            throw new UnauthorizedException("Unknown OAuth client");
        }
        if (!properties.supportsRedirectUri(request.redirect_uri())) {
            throw new BadRequestException("redirect_uri is not allowed");
        }
        if (!"S256".equals(request.code_challenge_method())) {
            throw new BadRequestException("Only S256 PKCE is supported");
        }

        Set<String> requestedScopes = parseScopes(request.scope());
        if (requestedScopes.isEmpty()) {
            requestedScopes = new LinkedHashSet<>(properties.getScopes());
        }
        if (!properties.supportsScopes(requestedScopes)) {
            throw new BadRequestException("Requested scope is not allowed");
        }

        return new ValidatedAuthorizationRequest(
                request.client_id(),
                request.redirect_uri(),
                requestedScopes,
                request.state(),
                request.code_challenge(),
                request.code_challenge_method()
        );
    }

    public void validateClientAuthentication(GptTokenRequest request, String authorizationHeader) {
        properties.validateConfigured();
        ClientCredentials credentials = extractCredentials(request, authorizationHeader);
        if (!properties.getClientId().equals(credentials.clientId())) {
            throw new UnauthorizedException("Invalid client credentials");
        }
        if (!properties.getClientSecret().equals(credentials.clientSecret())) {
            throw new UnauthorizedException("Invalid client credentials");
        }
    }

    private ClientCredentials extractCredentials(GptTokenRequest request, String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(authorizationHeader.substring(6)), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            if (parts.length == 2) {
                return new ClientCredentials(parts[0], parts[1]);
            }
        }
        if (hasText(request.client_id()) && hasText(request.client_secret())) {
            return new ClientCredentials(request.client_id(), request.client_secret());
        }
        throw new UnauthorizedException("Client authentication is required");
    }

    private Set<String> parseScopes(String scopeValue) {
        Set<String> scopes = new LinkedHashSet<>();
        if (!hasText(scopeValue)) {
            return scopes;
        }
        for (String scope : scopeValue.split(SCOPE_DELIMITER)) {
            String trimmed = scope.trim();
            if (!trimmed.isBlank()) {
                scopes.add(trimmed);
            }
        }
        return scopes;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ValidatedAuthorizationRequest(
            String clientId,
            String redirectUri,
            Set<String> scopes,
            String state,
            String codeChallenge,
            String codeChallengeMethod
    ) {
        public String scopeValue() {
            return String.join(SCOPE_DELIMITER, scopes);
        }
    }

    private record ClientCredentials(String clientId, String clientSecret) {
    }
}
