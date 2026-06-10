package com.jobtracker.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * Authenticates public clients (method {@code none}) on {@code grant_type=refresh_token}
 * requests.
 *
 * <p>Spring Authorization Server's built-in {@code PublicClientAuthenticationConverter}
 * only matches PKCE authorization_code token requests, so a public client presenting
 * just its {@code client_id} to refresh a token gets {@code invalid_client}. MCP
 * connectors (Claude, ChatGPT) are public PKCE clients and refresh exactly this way.
 *
 * <p>Deliberately defensive: it only converts when no other client authentication is
 * present (no {@code Authorization} header, no {@code client_secret} parameter), so
 * confidential clients keep flowing through the standard converters.
 */
public final class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    /**
     * Marker subclass so {@link PublicClientRefreshTokenAuthenticationProvider} handles
     * only tokens produced here — the built-in {@code PublicClientAuthenticationProvider}
     * would reject a plain NONE token without a {@code code_verifier}.
     */
    static final class PublicClientRefreshTokenAuthentication extends OAuth2ClientAuthenticationToken {
        PublicClientRefreshTokenAuthentication(String clientId) {
            super(clientId, ClientAuthenticationMethod.NONE, null, null);
        }
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue()
                .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }
        // another client authentication method is in play — not ours
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
            return null;
        }
        String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length != 1 || !StringUtils.hasText(clientIds[0])) {
            return null;
        }
        return new PublicClientRefreshTokenAuthentication(clientIds[0]);
    }
}
