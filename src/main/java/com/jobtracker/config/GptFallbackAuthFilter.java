package com.jobtracker.config;

import com.jobtracker.entity.Role;
import com.jobtracker.entity.User;
import com.jobtracker.entity.enums.RoleName;
import com.jobtracker.repository.RoleRepository;
import com.jobtracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GptFallbackAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String GPT_FALLBACK_USER_EMAIL = "gpt-fallback@jobtracker.local";
    private static final String GPT_FALLBACK_USER_NAME = "GPT Fallback";
    private static final List<GrantedAuthority> GPT_SCOPE_AUTHORITIES = List.of(
            new SimpleGrantedAuthority("SCOPE_read:profile"),
            new SimpleGrantedAuthority("SCOPE_read:applications"),
            new SimpleGrantedAuthority("SCOPE_write:applications"),
            new SimpleGrantedAuthority("SCOPE_read:resume"),
            new SimpleGrantedAuthority("SCOPE_read:google-drive"),
            new SimpleGrantedAuthority("SCOPE_read:metrics"),
            new SimpleGrantedAuthority("ROLE_GPT_CLIENT"));

    private final GptFallbackAuthProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public GptFallbackAuthFilter(
            GptFallbackAuthProperties properties,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            String configuredToken = properties.getToken();

            if (authorizationHeader != null
                    && authorizationHeader.startsWith(BEARER_PREFIX)
                    && configuredToken != null
                    && !configuredToken.isBlank()) {
                String providedToken = authorizationHeader.substring(BEARER_PREFIX.length());
                if (isMatchingToken(configuredToken, providedToken)) {
                    org.springframework.security.core.userdetails.UserDetails userDetails = ensureFallbackUser();
                    Set<GrantedAuthority> authorities = new LinkedHashSet<>(userDetails.getAuthorities());
                    authorities.addAll(GPT_SCOPE_AUTHORITIES);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            providedToken,
                            authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isMatchingToken(String expectedToken, String actualToken) {
        byte[] expectedBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actualToken.getBytes(StandardCharsets.UTF_8);
        return expectedBytes.length == actualBytes.length && MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private org.springframework.security.core.userdetails.UserDetails ensureFallbackUser() {
        User user = userRepository.findByEmail(GPT_FALLBACK_USER_EMAIL).orElseGet(() -> {
            User newUser = new User();
            newUser.setName(GPT_FALLBACK_USER_NAME);
            newUser.setEmail(GPT_FALLBACK_USER_EMAIL);
            newUser.setPasswordHash(passwordEncoder.encode(GPT_FALLBACK_USER_EMAIL));
            newUser.setRoles(resolveFallbackRoles());
            return userRepository.save(newUser);
        });

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setRoles(resolveFallbackRoles());
            user = userRepository.save(user);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                        .collect(Collectors.toSet()));
    }

    private Set<Role> resolveFallbackRoles() {
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseGet(() -> saveRole(RoleName.USER));
        Role betaRole = roleRepository.findByName(RoleName.BETA)
                .orElseGet(() -> saveRole(RoleName.BETA));
        return new LinkedHashSet<>(List.of(userRole, betaRole));
    }

    private Role saveRole(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        return roleRepository.save(role);
    }
}
