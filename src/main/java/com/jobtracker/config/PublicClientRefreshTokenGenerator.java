package com.jobtracker.config;

import org.springframework.lang.Nullable;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.Base64;

/**
 * Refresh token generator that, unlike Spring Authorization Server's default
 * {@code OAuth2RefreshTokenGenerator}, also issues refresh tokens to public clients
 * (client authentication method {@code none}).
 *
 * <p>The default generator silently skips public clients on the authorization_code
 * grant, which breaks MCP connectors: Claude and ChatGPT are public PKCE clients, the
 * access token lives for 15 minutes, and without a refresh token the connector dies
 * at the first expiry (symptom: it works right after connecting, then turns into
 * anonymous 401s / empty tools).
 *
 * <p>This follows the OAuth 2.1 guidance for public clients: refresh tokens are
 * allowed provided they are sender-constrained or <em>rotated</em> — rotation is on
 * for every MCP-facing client here ({@code reuseRefreshTokens=false}), so a leaked
 * refresh token is invalidated on first legitimate use.
 */
public final class PublicClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final StringKeyGenerator refreshTokenGenerator =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    @Nullable
    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(this.refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
    }
}
