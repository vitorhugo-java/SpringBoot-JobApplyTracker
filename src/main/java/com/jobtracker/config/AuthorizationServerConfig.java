package com.jobtracker.config;

import com.jobtracker.repository.UserRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.UUID;

@Configuration
@EnableConfigurationProperties(GptOAuthProperties.class)
public class AuthorizationServerConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();
        RequestMatcher authServerMatcher = new OrRequestMatcher(endpointsMatcher, request -> "/login".equals(request.getServletPath()));

        http
                .securityMatcher(authServerMatcher)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated())
                .with(authorizationServerConfigurer, authorizationServer -> authorizationServer.oidc(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        request -> "/oauth2/token".equals(request.getServletPath()),
                        request -> "/oauth2/revoke".equals(request.getServletPath()),
                        request -> "/oauth2/introspect".equals(request.getServletPath())))
                .formLogin(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(GptOAuthProperties properties) {
        return AuthorizationServerSettings.builder()
                .issuer(properties.normalizedIssuer())
                .build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(@Value("${jwt.secret}") String jwtSecret) {
        try {
            byte[] seed = MessageDigest.getInstance("SHA-256")
                    .digest(("authorization-server-rsa::" + jwtSecret).getBytes(StandardCharsets.UTF_8));
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seed);

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048, secureRandom);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("authorization-server-rsa")
                    .build();
            return new ImmutableJWKSet<>(new JWKSet(rsaKey));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to initialize authorization server signing key", ex);
        }
    }

    @Bean
    public JwtDecoder authorizationServerJwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(UserRepository userRepository) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            LinkedHashSet<String> roles = new LinkedHashSet<>();
            roles.add("ROLE_GPT_CLIENT");
            context.getPrincipal().getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .filter(authority -> !"ROLE_USER".equals(authority))
                    .forEach(roles::add);

            context.getClaims().claim("roles", roles);
            userRepository.findByEmail(context.getPrincipal().getName())
                    .ifPresent(user -> context.getClaims().claim("user_id", user.getId().toString()));
        };
    }

    @Bean
    public ApplicationRunner registeredClientBootstrap(
            GptOAuthProperties properties,
            RegisteredClientRepository registeredClientRepository,
            JdbcOperations jdbcOperations,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (!properties.isConfigured()) {
                return;
            }

            RegisteredClient registeredClient = RegisteredClient.withId(stableId(properties.getClientId()))
                    .clientId(properties.getClientId())
                    .clientIdIssuedAt(Instant.now())
                    .clientSecret(passwordEncoder.encode(properties.getClientSecret()))
                    .clientName("OpenAI GPT Actions")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUris(uris -> uris.addAll(properties.getRedirectUris()))
                    .scopes(scopes -> scopes.addAll(properties.getScopes()))
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(false)
                            .requireProofKey(true)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .authorizationCodeTimeToLive(properties.getAuthorizationCodeTimeToLive())
                            .accessTokenTimeToLive(properties.getAccessTokenTimeToLive())
                            .refreshTokenTimeToLive(properties.getRefreshTokenTimeToLive())
                            .reuseRefreshTokens(false)
                            .build())
                    .build();

            RegisteredClient existing = registeredClientRepository.findByClientId(properties.getClientId());
            if (existing == null) {
                registeredClientRepository.save(registeredClient);
                return;
            }

            if (registeredClientRequiresRefresh(existing, properties, passwordEncoder)) {
                jdbcOperations.update("DELETE FROM oauth2_registered_client WHERE id = ?", existing.getId());
                registeredClientRepository.save(registeredClient);
            }
        };
    }

    private static String stableId(String clientId) {
        return UUID.nameUUIDFromBytes(clientId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean registeredClientRequiresRefresh(
            RegisteredClient existing,
            GptOAuthProperties properties,
            PasswordEncoder passwordEncoder) {
        return !passwordEncoder.matches(properties.getClientSecret(), existing.getClientSecret())
                || !new LinkedHashSet<>(existing.getRedirectUris()).equals(new LinkedHashSet<>(properties.getRedirectUris()))
                || !new LinkedHashSet<>(existing.getScopes()).equals(new LinkedHashSet<>(properties.getScopes()))
                || !existing.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                || !existing.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                || !existing.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)
                || !existing.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)
                || !existing.getClientSettings().isRequireProofKey();
    }
}
