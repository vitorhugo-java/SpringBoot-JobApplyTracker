package com.jobtracker.config;

import com.jobtracker.entity.Role;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.RoleName;
import com.jobtracker.repository.RoleRepository;
import com.jobtracker.repository.UserRepository;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(GptFallbackAuthProperties.class)
public class SecurityConfig {

    private static final String GPT_FALLBACK_USER_EMAIL = "gpt-fallback@jobtracker.local";
    private static final String GPT_FALLBACK_USER_NAME = "GPT Fallback";

    private final RequestLoggingFilter requestLoggingFilter;

    public SecurityConfig(RequestLoggingFilter requestLoggingFilter) {
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
                .addFilterBefore(requestLoggingFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManagerResolver<HttpServletRequest> apiAuthenticationManagerResolver(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            JwtDecoder authorizationServerJwtDecoder,
            Converter<Jwt, ? extends org.springframework.security.authentication.AbstractAuthenticationToken> apiJwtAuthenticationConverter,
            GptFallbackAuthProperties gptFallbackAuthProperties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        AuthenticationManager fallbackAuthenticationManager = authentication -> {
            if (!(authentication instanceof BearerTokenAuthenticationToken bearerTokenAuthenticationToken)) {
                throw new BadCredentialsException("Unsupported authentication token");
            }

            if (!gptFallbackAuthProperties.isConfigured()
                    || !isMatchingToken(gptFallbackAuthProperties.getToken(), bearerTokenAuthenticationToken.getToken())) {
                throw new BadCredentialsException("Invalid GPT fallback bearer token");
            }

            UserDetails userDetails = ensureFallbackUser(userRepository, roleRepository, passwordEncoder);
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(userDetails.getAuthorities());
            authorities.add(new SimpleGrantedAuthority("ROLE_GPT_CLIENT"));
            return new UsernamePasswordAuthenticationToken(
                    userDetails,
                    bearerTokenAuthenticationToken.getToken(),
                    authorities);
        };

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
                return fallbackAuthenticationManager.authenticate(authentication);
            } catch (Exception ex) {
                // Fall through to the existing JWT-based authentication paths.
            }

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

    private static boolean isMatchingToken(String expectedToken, String actualToken) {
        if (expectedToken == null || actualToken == null) {
            return false;
        }

        byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actualToken.getBytes(StandardCharsets.UTF_8);
        return expectedBytes.length == actualBytes.length && MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private UserDetails ensureFallbackUser(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        User user = userRepository.findByEmail(GPT_FALLBACK_USER_EMAIL).orElseGet(() -> {
            User newUser = new User();
            newUser.setName(GPT_FALLBACK_USER_NAME);
            newUser.setEmail(GPT_FALLBACK_USER_EMAIL);
            newUser.setPasswordHash(passwordEncoder.encode(GPT_FALLBACK_USER_EMAIL));
            newUser.setRoles(resolveFallbackRoles(roleRepository));
            return userRepository.save(newUser);
        });

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setRoles(resolveFallbackRoles(roleRepository));
            user = userRepository.save(user);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                        .collect(Collectors.toSet()));
    }

    private Set<Role> resolveFallbackRoles(RoleRepository roleRepository) {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseGet(() -> saveRole(roleRepository, RoleName.USER));
        Role betaRole = roleRepository.findByName(RoleName.BETA)
                .orElseGet(() -> saveRole(roleRepository, RoleName.BETA));
        return new LinkedHashSet<>(List.of(userRole, betaRole));
    }

    private Role saveRole(RoleRepository roleRepository, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        return roleRepository.save(role);
    }
}
