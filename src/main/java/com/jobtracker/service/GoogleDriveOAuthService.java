package com.jobtracker.service;

import com.jobtracker.config.GoogleDriveProperties;
import com.jobtracker.dto.auth.MessageResponse;
import com.jobtracker.dto.gdrive.GoogleDriveOAuthStartResponse;
import com.jobtracker.entity.GoogleDriveConnection;
import com.jobtracker.entity.GoogleDriveOAuthState;
import com.jobtracker.entity.User;
import com.jobtracker.exception.BadRequestException;
import com.jobtracker.repository.GoogleDriveConnectionRepository;
import com.jobtracker.repository.GoogleDriveOAuthStateRepository;
import com.jobtracker.util.SecurityUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class GoogleDriveOAuthService {

    private static final int OAUTH_STATE_TTL_MINUTES = 10;

    private final GoogleDriveApiClient googleDriveApiClient;
    private final GoogleDriveProperties googleDriveProperties;
    private final GoogleDriveConnectionRepository connectionRepository;
    private final GoogleDriveOAuthStateRepository oauthStateRepository;
    private final SecurityUtils securityUtils;

    public GoogleDriveOAuthService(GoogleDriveApiClient googleDriveApiClient,
                                   GoogleDriveProperties googleDriveProperties,
                                   GoogleDriveConnectionRepository connectionRepository,
                                   GoogleDriveOAuthStateRepository oauthStateRepository,
                                   SecurityUtils securityUtils) {
        this.googleDriveApiClient = googleDriveApiClient;
        this.googleDriveProperties = googleDriveProperties;
        this.connectionRepository = connectionRepository;
        this.oauthStateRepository = oauthStateRepository;
        this.securityUtils = securityUtils;
    }

    @Transactional
    public GoogleDriveOAuthStartResponse startAuthorization() {
        validateServerConfigured();
        oauthStateRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        User currentUser = securityUtils.getCurrentUser();
        String state = generateState(currentUser.getId());

        GoogleDriveOAuthState oauthState = new GoogleDriveOAuthState();
        oauthState.setUser(currentUser);
        oauthState.setStateToken(state);
        oauthState.setExpiresAt(LocalDateTime.now().plusMinutes(OAUTH_STATE_TTL_MINUTES));
        oauthStateRepository.save(oauthState);

        return new GoogleDriveOAuthStartResponse(
                googleDriveApiClient.buildAuthorizationUrl(state),
                state,
                googleDriveProperties.getRedirectUri(),
                googleDriveProperties.getScopes()
        );
    }

    @Transactional
    public String handleCallback(String state, String code, String error) {
        validateServerConfigured();
        oauthStateRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        if (error != null && !error.isBlank()) {
            return buildFrontendRedirect("error", "Google returned an OAuth error: " + error);
        }
        if (state == null || state.isBlank()) {
            return buildFrontendRedirect("error", "Missing OAuth state");
        }
        if (code == null || code.isBlank()) {
            return buildFrontendRedirect("error", "Missing authorization code");
        }

        GoogleDriveOAuthState oauthState = null;
        try {
            oauthState = oauthStateRepository.findByStateToken(state)
                    .orElseThrow(() -> new BadRequestException("Invalid or expired Google OAuth state"));
            if (oauthState.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BadRequestException("Google OAuth state expired");
            }

            GoogleDriveApiClient.OAuthTokens tokens = googleDriveApiClient.exchangeAuthorizationCode(code);
            GoogleDriveApiClient.GoogleDriveAccountProfile accountProfile =
                    googleDriveApiClient.getCurrentAccount(tokens.accessToken());

            GoogleDriveConnection connection = connectionRepository.findByUserId(oauthState.getUser().getId())
                    .orElseGet(GoogleDriveConnection::new);
            boolean accountChanged = connection.getGoogleAccountId() != null
                    && !connection.getGoogleAccountId().equals(accountProfile.accountId());

            connection.setUser(oauthState.getUser());
            connection.setGoogleAccountId(accountProfile.accountId());
            connection.setGoogleEmail(accountProfile.emailAddress());
            connection.setGoogleDisplayName(accountProfile.displayName());
            connection.setAccessToken(tokens.accessToken());
            connection.setRefreshToken(tokens.refreshToken());
            connection.setAccessTokenExpiresAt(tokens.accessTokenExpiresAt());
            connection.setGrantedScopes(tokens.scope() == null || tokens.scope().isBlank()
                    ? googleDriveProperties.getScopeValue() : tokens.scope());
            connection.setConnectedAt(LocalDateTime.now());
            if (accountChanged) {
                connection.setRootFolderId(null);
                connection.setRootFolderName(null);
                connection.getBaseResumes().clear();
            }
            connectionRepository.save(connection);

            return buildFrontendRedirect("success", "Google Drive connected successfully");
        } catch (BadRequestException ex) {
            return buildFrontendRedirect("error", ex.getMessage());
        } finally {
            if (oauthState != null) {
                oauthStateRepository.delete(oauthState);
            }
        }
    }

    @Transactional
    public MessageResponse disconnect() {
        connectionRepository.findByUserId(securityUtils.getCurrentUserId())
                .ifPresent(connectionRepository::delete);
        return new MessageResponse("Google Drive connection removed");
    }

    private void validateServerConfigured() {
        try {
            googleDriveProperties.validateConfigured();
        } catch (IllegalStateException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    private String generateState(UUID userId) {
        String raw = userId + ":" + UUID.randomUUID() + ":" + System.nanoTime();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(DigestUtils.sha256(raw));
    }

    private String buildFrontendRedirect(String status, String message) {
        return UriComponentsBuilder.fromUriString(googleDriveProperties.getOauthCompleteUrl())
                .queryParam("status", status)
                .queryParam("message", message)
                .build()
                .encode()
                .toUriString();
    }
}
