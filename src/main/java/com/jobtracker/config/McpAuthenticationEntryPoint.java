package com.jobtracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Authentication entry point for the MCP resource server.
 *
 * <p>MCP clients (ChatGPT, Claude) bootstrap OAuth from the resource server's
 * {@code 401} challenge: per RFC 6750 / RFC 9728 the response to an
 * unauthenticated request MUST be {@code 401 Unauthorized} carrying a
 * {@code WWW-Authenticate: Bearer ... resource_metadata="<url>"} header that
 * points at the protected-resource metadata document. Returning a bare
 * {@code 403} (as the previous hard-coded entry point did) gives the client no
 * way to discover the authorization server, so the connector fails with
 * "Authorization with the MCP server failed".
 *
 * <p>Only {@code /mcp/**} requests receive the Bearer challenge. Every other
 * path keeps the existing {@code 403 Forbidden} behaviour so the REST API and
 * web frontend contract is unchanged.
 */
public class McpAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MCP_PATH_PREFIX = "/mcp/";
    private static final String PROTECTED_RESOURCE_METADATA_PREFIX = "/.well-known/oauth-protected-resource";

    private final String issuer;

    public McpAuthenticationEntryPoint(String issuer) {
        this.issuer = issuer;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String path = request.getRequestURI();
        if (path != null && path.startsWith(MCP_PATH_PREFIX)) {
            // RFC 9728 §5.1: advertise the resource-specific metadata document.
            String resourceMetadataUrl = issuer + PROTECTED_RESOURCE_METADATA_PREFIX + path;
            response.setHeader("WWW-Authenticate",
                    "Bearer resource_metadata=\"" + resourceMetadataUrl + "\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
