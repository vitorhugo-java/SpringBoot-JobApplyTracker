package com.jobtracker.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashSet;

@Configuration
public class GptOAuthSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain gptOAuthSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**")
                .csrf(csrf -> csrf.ignoringRequestMatchers("/oauth2/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/oauth2/authorize", "/oauth2/token").permitAll()
                        .anyRequest().denyAll());

        return http.build();
    }

    @Bean
    public SecretKey gptOAuthSecretKey(@org.springframework.beans.factory.annotation.Value("${jwt.secret}") String jwtSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("gpt-oauth::" + jwtSecret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "HmacSHA256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to initialize GPT OAuth signing key", ex);
        }
    }

    @Bean
    public JwtEncoder gptOAuthJwtEncoder(SecretKey gptOAuthSecretKey) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(gptOAuthSecretKey)
                .keyID("gpt-oauth-hmac")
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder gptOAuthJwtDecoder(SecretKey gptOAuthSecretKey, GptOAuthProperties properties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(gptOAuthSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                audienceValidator(properties.getAudience()),
                tokenUseValidator()
        );
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> gptJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthoritiesClaimName("scope");
        scopeConverter.setAuthorityPrefix("SCOPE_");

        return jwt -> {
            Collection<GrantedAuthority> authorities = new LinkedHashSet<>(scopeConverter.convert(jwt));
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (role instanceof String roleValue) {
                        authorities.add(new SimpleGrantedAuthority(roleValue));
                    }
                }
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return token -> {
            if (token.getAudience() != null && token.getAudience().contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid GPT OAuth audience", null));
        };
    }

    private OAuth2TokenValidator<Jwt> tokenUseValidator() {
        return token -> "gpt_action_access".equals(token.getClaimAsString("token_use"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid GPT OAuth token_use", null));
    }
}
