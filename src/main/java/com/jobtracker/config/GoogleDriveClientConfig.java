package com.jobtracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

/**
 * Configures the Spring OAuth2 client infrastructure used by the Google Drive integration.
 * <p>
 * The {@link ClientRegistration} bean captures all Google OAuth2 parameters in one place so that
 * Spring's standard {@link DefaultAuthorizationCodeTokenResponseClient} and
 * {@link DefaultRefreshTokenTokenResponseClient} can handle token exchange and refresh without any
 * manual HTTP calls.
 * <p>
 * <b>How the Spring Security context provides the authorized client:</b><br>
 * When a user completes the OAuth2 consent flow, {@code GoogleDriveOAuthService.handleCallback()}
 * calls {@code authorizationCodeTokenResponseClient.getTokenResponse(grantRequest)}.  The resulting
 * access and refresh tokens are stored in our {@code google_drive_connections} table via
 * {@code GoogleDriveConnectionRepository}.  For every subsequent Drive API call,
 * {@code GoogleDriveService} loads the connection, refreshes the token if needed using
 * {@code refreshTokenResponseClient}, and passes the fresh access token to {@link
 * com.jobtracker.service.DriveClientFactory#create} – which wraps it in a Google
 * {@link com.google.auth.oauth2.OAuth2Credentials} so the SDK can authenticate automatically.
 */
@Configuration
public class GoogleDriveClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    /**
     * The {@link ClientRegistration} for Google Drive. Built programmatically from
     * {@link GoogleDriveProperties} so we have a single source of truth for OAuth2 parameters.
     * Note: a {@code ClientRegistrationRepository} bean is intentionally <em>not</em> created;
     * that would trigger Spring Security's OAuth2 login auto-configuration which is incompatible
     * with this application's stateless JWT architecture.
     */
    @Bean
    public ClientRegistration googleDriveClientRegistration(GoogleDriveProperties properties) {
        return ClientRegistration.withRegistrationId("google-drive")
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.getRedirectUri())
                .scope(properties.getScopes().toArray(new String[0]))
                .authorizationUri(properties.getAuthorizationUri())
                .tokenUri(properties.getTokenUri())
                .build();
    }

    /**
     * Token response client for the Authorization Code grant. Replaces manual RestClient HTTP
     * calls with Spring Security's type-safe implementation. Configured with the same timeouts
     * used elsewhere in the application.
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> authorizationCodeTokenResponseClient() {
        DefaultAuthorizationCodeTokenResponseClient client = new DefaultAuthorizationCodeTokenResponseClient();
        client.setRestOperations(buildRestTemplate());
        return client;
    }

    /**
     * Token response client for the Refresh Token grant. Used by {@code GoogleDriveService} to
     * silently renew expired access tokens before each Drive API call.
     */
    @Bean
    public OAuth2AccessTokenResponseClient<OAuth2RefreshTokenGrantRequest> refreshTokenResponseClient() {
        DefaultRefreshTokenTokenResponseClient client = new DefaultRefreshTokenTokenResponseClient();
        client.setRestOperations(buildRestTemplate());
        return client;
    }

    /**
     * Builds a {@link RestTemplate} configured with the message converters and error handler
     * required by Spring Security's OAuth2 token endpoint clients.
     * <p>
     * {@link FormHttpMessageConverter} encodes the token-exchange form body; without it the
     * request body is empty and Google returns {@code invalid_grant}.
     * {@link OAuth2AccessTokenResponseHttpMessageConverter} parses Google's JSON token response;
     * without it the response body cannot be deserialized and the exchange fails.
     * {@link OAuth2ErrorResponseErrorHandler} translates Google error responses into typed
     * {@link org.springframework.security.oauth2.core.OAuth2AuthorizationException}s rather than
     * raw {@link org.springframework.web.client.HttpClientErrorException}s.
     */
    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(CONNECT_TIMEOUT_MS).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(READ_TIMEOUT_MS).toMillis());
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setMessageConverters(List.of(
                new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter()
        ));
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        return restTemplate;
    }
}
