package com.jobtracker.mcp.audit;

import com.knuddels.jtokkit.api.EncodingType;

/**
 * Normalized identity of the AI client (MCP provider) that invoked a tool.
 *
 * <p>The raw client name comes from the MCP {@code initialize} handshake
 * ({@code McpSyncRequestContext.clientInfo().name()}, e.g. {@code "claude-code"}).
 * {@link #fromClientName(String)} maps that to a bounded enum so it is safe to use as a
 * Prometheus tag, and {@link #encoding()} selects the JTokkit encoding used for token
 * estimation.
 *
 * <p>JTokkit only ships OpenAI (tiktoken) encodings — there is no native Claude or Gemini
 * tokenizer — so non-OpenAI providers fall back to {@code CL100K_BASE} as a heuristic, which
 * also keeps token counts comparable with what the codebase produced before.
 */
public enum McpProvider {

    CHATGPT(EncodingType.O200K_BASE),
    CLAUDE(EncodingType.CL100K_BASE),
    GEMINI(EncodingType.CL100K_BASE),
    UNKNOWN(EncodingType.CL100K_BASE);

    private final EncodingType encoding;

    McpProvider(EncodingType encoding) {
        this.encoding = encoding;
    }

    /** The JTokkit encoding used to estimate token counts for this provider. */
    public EncodingType encoding() {
        return encoding;
    }

    /**
     * Normalizes a raw MCP client name (or an explicit annotation override) into a provider.
     * Matching is case-insensitive and substring-based; unrecognized or blank input yields
     * {@link #UNKNOWN}.
     */
    public static McpProvider fromClientName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return UNKNOWN;
        }
        String n = rawName.trim().toLowerCase();

        // Explicit enum names take precedence (annotation override may pass "CLAUDE", etc.).
        if (n.equals("chatgpt") || n.equals("claude") || n.equals("gemini") || n.equals("unknown")) {
            return valueOf(n.toUpperCase());
        }
        if (n.contains("claude") || n.contains("anthropic")) {
            return CLAUDE;
        }
        if (n.contains("chatgpt") || n.contains("openai") || n.contains("gpt")) {
            return CHATGPT;
        }
        if (n.contains("gemini") || n.contains("google") || n.contains("bard")) {
            return GEMINI;
        }
        return UNKNOWN;
    }
}
