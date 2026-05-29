package com.jobtracker.unit;

import com.jobtracker.config.GptOAuthProperties;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GptOAuthPropertiesTest {

    @Test
    void shouldParseConfiguredRedirectUrisAndScopes() {
        GptOAuthProperties properties = new GptOAuthProperties(
                "openai-client",
                "openai-secret",
                "https://chat.openai.com/aip/callback-one, https://chat.openai.com/aip/callback-two",
                "read:profile, read:applications, write:applications",
                "https://jobapply-api.hugojava.dev",
                "jobtracker-gpt-actions",
                300,
                900
        );

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.getRedirectUris())
                .containsExactly("https://chat.openai.com/aip/callback-one", "https://chat.openai.com/aip/callback-two");
        assertThat(properties.getScopes())
                .containsExactly("read:profile", "read:applications", "write:applications");
        assertThat(properties.supportsRedirectUri("https://chat.openai.com/aip/callback-two")).isTrue();
        assertThat(properties.supportsScopes(Set.of("read:profile", "write:applications"))).isTrue();
        assertThat(properties.supportsScopes(Set.of("read:metrics"))).isFalse();
    }
}
