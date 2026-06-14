package com.jobtracker.unit.mcp;

import com.jobtracker.mcp.audit.McpProvider;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpProviderTest {

    @Test
    void fromClientName_normalizesRealMcpClientNames() {
        assertThat(McpProvider.fromClientName("claude-code")).isEqualTo(McpProvider.CLAUDE);
        assertThat(McpProvider.fromClientName("Claude Desktop")).isEqualTo(McpProvider.CLAUDE);
        assertThat(McpProvider.fromClientName("chatgpt")).isEqualTo(McpProvider.CHATGPT);
        assertThat(McpProvider.fromClientName("openai-gpt")).isEqualTo(McpProvider.CHATGPT);
        assertThat(McpProvider.fromClientName("gemini-cli")).isEqualTo(McpProvider.GEMINI);
    }

    @Test
    void fromClientName_handlesExplicitEnumOverrides() {
        assertThat(McpProvider.fromClientName("CLAUDE")).isEqualTo(McpProvider.CLAUDE);
        assertThat(McpProvider.fromClientName("CHATGPT")).isEqualTo(McpProvider.CHATGPT);
    }

    @Test
    void fromClientName_unknownOrBlank_yieldsUnknown() {
        assertThat(McpProvider.fromClientName(null)).isEqualTo(McpProvider.UNKNOWN);
        assertThat(McpProvider.fromClientName("")).isEqualTo(McpProvider.UNKNOWN);
        assertThat(McpProvider.fromClientName("some-random-client")).isEqualTo(McpProvider.UNKNOWN);
    }

    @Test
    void encoding_selectsOpenAiEncodingForChatGpt_andFallsBackForOthers() {
        // ChatGPT uses the current OpenAI encoding; JTokkit has no Claude/Gemini tokenizer
        // so those fall back to the CL100K_BASE heuristic.
        assertThat(McpProvider.CHATGPT.encoding()).isEqualTo(EncodingType.O200K_BASE);
        assertThat(McpProvider.CLAUDE.encoding()).isEqualTo(EncodingType.CL100K_BASE);
        assertThat(McpProvider.GEMINI.encoding()).isEqualTo(EncodingType.CL100K_BASE);
        assertThat(McpProvider.UNKNOWN.encoding()).isEqualTo(EncodingType.CL100K_BASE);
    }
}
