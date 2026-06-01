package com.jobtracker.unit;

import com.jobtracker.config.GptOAuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GptOAuthPropertiesTest {

    @Test
    void shouldParseConfiguredRedirectUrisAndScopes() {
        GptOAuthProperties properties = new GptOAuthProperties();
        properties.setClientId("openai-client");
        properties.setClientSecret("openai-secret");
        properties.setRedirectUris(java.util.List.of(
                "https://chat.openai.com/aip/callback-one",
                " https://chat.openai.com/aip/callback-two"));
        properties.setScopes(java.util.List.of("openid", "read:profile", "read:applications", "write:applications"));
        properties.setIssuer("https://jobapply-api.hugojava.dev/");
        properties.setAuthorizationCodeExpirationSeconds(300);
        properties.setAccessTokenExpirationSeconds(900);

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.getRedirectUris())
                .containsExactly("https://chat.openai.com/aip/callback-one", "https://chat.openai.com/aip/callback-two");
        assertThat(properties.getScopes())
                .containsExactly("openid", "read:profile", "read:applications", "write:applications");
        assertThat(properties.scopeSet()).contains("read:profile", "write:applications");
        assertThat(properties.normalizedIssuer()).isEqualTo("https://jobapply-api.hugojava.dev");
        assertThat(properties.getAuthorizationCodeTimeToLive()).hasSeconds(300);
        assertThat(properties.getAccessTokenTimeToLive()).hasSeconds(900);
    }
}
