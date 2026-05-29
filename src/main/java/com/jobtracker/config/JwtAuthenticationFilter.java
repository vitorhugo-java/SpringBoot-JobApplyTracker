package com.jobtracker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtDecoder gptOAuthJwtDecoder;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> gptJwtAuthenticationConverter;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserDetailsService userDetailsService,
                                   JwtDecoder gptOAuthJwtDecoder,
                                   Converter<Jwt, ? extends AbstractAuthenticationToken> gptJwtAuthenticationConverter) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.gptOAuthJwtDecoder = gptOAuthJwtDecoder;
        this.gptJwtAuthenticationConverter = gptJwtAuthenticationConverter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);
        if (!tryUserJwt(token, request)) {
            tryGptOAuthToken(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryUserJwt(String token, HttpServletRequest request) {
        try {
            final String userEmail = jwtService.extractUsername(token);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = null;
                try {
                    userDetails = userDetailsService.loadUserByUsername(userEmail);
                } catch (UsernameNotFoundException e) {
                    log.debug("JWT authentication failed: user not found for email={}", userEmail);
                    return false;
                }
                if (userDetails != null && jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, jwtService.extractAuthorities(token));
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    return true;
                }
                log.debug("User JWT authentication failed: token invalid for email={}", userEmail);
                return false;
            }
        } catch (Exception e) {
            // Log invalid user JWT at debug level; fall through to GPT token attempt
            log.debug("User JWT authentication failed: {}", e.getMessage());
        }
        return false;
    }

    private void tryGptOAuthToken(String token, HttpServletRequest request) {
        try {
            Jwt jwt = gptOAuthJwtDecoder.decode(token);
            AbstractAuthenticationToken authToken =
                    (AbstractAuthenticationToken) gptJwtAuthenticationConverter.convert(jwt);
            if (authToken != null) {
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception e) {
            log.debug("GPT OAuth token authentication failed: {}", e.getMessage());
        }
    }
}
