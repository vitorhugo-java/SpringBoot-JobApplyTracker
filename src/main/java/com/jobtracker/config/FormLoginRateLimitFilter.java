package com.jobtracker.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Rate-limits the authorization server's form login ({@code POST /login}).
 *
 * <p>The REST auth endpoints are covered by {@code @RateLimiter} annotations on
 * {@code AuthController}, but the Spring Authorization Server login form is handled by
 * {@code UsernamePasswordAuthenticationFilter} — there is no controller to annotate, so
 * brute-force protection has to happen at the filter level. This matters since Cloudflare
 * Bot Fight Mode was disabled (it challenged the MCP clients' backends), leaving this
 * endpoint otherwise unthrottled.
 *
 * <p>Uses the {@code formLogin} Resilience4j instance (same convention and response shape
 * as the controller limiters / {@code GlobalExceptionHandler}).
 */
@Component
public class FormLoginRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FormLoginRateLimitFilter.class);

    private final RateLimiter rateLimiter;

    public FormLoginRateLimitFilter(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiter = rateLimiterRegistry.rateLimiter("formLogin");
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimiter.acquirePermission()) {
            log.warn("event=RATE_LIMIT_EXCEEDED path=/login");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"timestamp":"%s","status":429,"error":"Too Many Requests",\
                    "message":"Too many requests. Please try again later."}"""
                    .formatted(LocalDateTime.now()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
