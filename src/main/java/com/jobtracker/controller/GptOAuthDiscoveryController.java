package com.jobtracker.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.jobtracker.config.GptOAuthProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
public class GptOAuthDiscoveryController {

    private final GptOAuthProperties properties;
    private final RSAKey gptOAuthRsaJwk;

    public GptOAuthDiscoveryController(GptOAuthProperties properties, RSAKey gptOAuthRsaJwk) {
        this.properties = properties;
        this.gptOAuthRsaJwk = gptOAuthRsaJwk;
    }

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openidConfiguration() {
        String issuer = normalizedIssuer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
        metadata.put("token_endpoint", issuer + "/oauth2/token");
        metadata.put("jwks_uri", issuer + "/oauth2/jwks");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code"));
        metadata.put("subject_types_supported", List.of("public"));
        metadata.put("token_endpoint_auth_methods_supported", List.of("client_secret_post", "client_secret_basic"));
        metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        metadata.put("scopes_supported", properties.getScopes());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(metadata);
    }

    @GetMapping("/oauth2/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        Map<String, Object> jwks = new JWKSet(gptOAuthRsaJwk.toPublicJWK()).toJSONObject();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(jwks);
    }

    private String normalizedIssuer() {
        String issuer = properties.getIssuer();
        if (issuer.endsWith("/")) {
            return issuer.substring(0, issuer.length() - 1);
        }
        return issuer;
    }
}
