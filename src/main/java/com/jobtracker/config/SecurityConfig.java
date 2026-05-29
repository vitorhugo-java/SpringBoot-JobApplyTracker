package com.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RequestLoggingFilter requestLoggingFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            RequestLoggingFilter requestLoggingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.requestLoggingFilter = requestLoggingFilter;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Keep the legacy JWT application chain after the dedicated GPT OAuth chain so
        // `/oauth2/**` and `/api/v1/gpt/**` stay isolated from the existing JWT filter.
        http
                .cors(Customizer.withDefaults())
                // CSRF is safe to disable: this API uses stateless JWT Bearer token
                // authentication,
                // not cookie-based sessions. CSRF attacks require session cookies and do not
                // apply here.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/passkey/login/options",
                                "/api/v1/auth/passkey/login/verify",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/oauth/callback").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Actuator is served on a dedicated management port (8081) that is never
                        // exposed to the host; security is enforced via Docker network isolation.
                        .requestMatchers("/actuator/**").permitAll()
                        // GPT OAuth tokens (ROLE_GPT_CLIENT) may access these specific endpoints.
                        // Method-level @PreAuthorize further enforces required scopes.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/applications/*/status").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/status").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/base-resumes").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/base-resumes/*/content").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/applications/*/generated-resumes/content").hasAnyRole("USER", "GPT_CLIENT")
                        // ROLE_USER endpoints: all remaining application APIs under /api/v1/**
                        // (including /api/v1/auth/me and /api/v1/auth/me/**).
                        .requestMatchers("/api/v1/**").hasRole("USER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestLoggingFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
