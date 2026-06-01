package com.jobtracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(GptFallbackAuthProperties.class)
public class SecurityConfig {

    private final GptFallbackAuthFilter gptFallbackAuthFilter;
    private final RequestLoggingFilter requestLoggingFilter;

    public SecurityConfig(
            GptFallbackAuthFilter gptFallbackAuthFilter,
            RequestLoggingFilter requestLoggingFilter) {
        this.gptFallbackAuthFilter = gptFallbackAuthFilter;
        this.requestLoggingFilter = requestLoggingFilter;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManagerResolver<HttpServletRequest> apiAuthenticationManagerResolver) throws Exception {
        http
                .cors(Customizer.withDefaults())
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
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/applications").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/*").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/applications/*/status").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/status").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/base-resumes").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/base-resumes/*/content").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-drive/applications/*/generated-resumes/content").hasAnyRole("USER", "GPT_CLIENT")
                        .requestMatchers("/api/v1/**").hasRole("USER")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN))
                        .authenticationManagerResolver(apiAuthenticationManagerResolver))
                .addFilterBefore(gptFallbackAuthFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(requestLoggingFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> apiAuthenticationManagerResolver(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            JwtDecoder authorizationServerJwtDecoder,
            Converter<Jwt, ? extends org.springframework.security.authentication.AbstractAuthenticationToken> apiJwtAuthenticationConverter) {

        AuthenticationManager legacyJwtAuthenticationManager = authentication -> {
            if (!(authentication instanceof BearerTokenAuthenticationToken bearerTokenAuthenticationToken)) {
                throw new BadCredentialsException("Unsupported authentication token");
            }

            String token = bearerTokenAuthenticationToken.getToken();
            String userEmail = jwtService.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            if (!jwtService.isTokenValid(token, userDetails)) {
                throw new BadCredentialsException("Invalid legacy JWT access token");
            }
            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    token,
                    jwtService.extractAuthorities(token));
        };

        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(authorizationServerJwtDecoder);
        jwtAuthenticationProvider.setJwtAuthenticationConverter(apiJwtAuthenticationConverter);
        AuthenticationManager authorizationServerAuthenticationManager = new ProviderManager(jwtAuthenticationProvider);

        AuthenticationManager compositeAuthenticationManager = authentication -> {
            try {
                return legacyJwtAuthenticationManager.authenticate(authentication);
            } catch (Exception ex) {
                return authorizationServerAuthenticationManager.authenticate(authentication);
            }
        };

        return request -> compositeAuthenticationManager;
    }

    @Bean
    public Converter<Jwt, ? extends org.springframework.security.authentication.AbstractAuthenticationToken> apiJwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(this::extractJwtAuthorities);
        return delegate;
    }

    private Collection<GrantedAuthority> extractJwtAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new LinkedHashSet<>();

        Object scopeClaim = jwt.getClaims().get("scope");
        if (scopeClaim instanceof String scopeValue) {
            for (String scope : scopeValue.split("\\s+")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }
        } else {
            List<String> scopeValues = jwt.getClaimAsStringList("scope");
            if (scopeValues != null) {
                scopeValues.stream()
                        .filter(scope -> scope != null && !scope.isBlank())
                        .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                        .forEach(authorities::add);
            }
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        return authorities;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
