package com.jobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Estimates token counts and serialized byte sizes for arbitrary objects.
 *
 * <p>Token counts use the JTokkit encoding that best matches the calling provider
 * (see {@link com.jobtracker.mcp.audit.McpProvider}); callers that don't care pass none and
 * get the {@code CL100K_BASE} default. JTokkit only ships OpenAI encodings, so providers
 * without a native tokenizer (Claude, Gemini) fall back to {@code CL100K_BASE}.
 *
 * <p>This is a cost-ranking heuristic — not a billing-accurate counter. Every method returns
 * 0 on any failure so callers are never blocked.
 */
@Service
public class TokenEstimatorService {

    private static final Logger log = LoggerFactory.getLogger(TokenEstimatorService.class);
    private static final EncodingType DEFAULT_ENCODING = EncodingType.CL100K_BASE;

    private final ObjectMapper objectMapper;
    private final EncodingRegistry registry;
    private final Map<EncodingType, Encoding> encodingCache = new EnumMap<>(EncodingType.class);

    public TokenEstimatorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.registry = Encodings.newDefaultEncodingRegistry();
    }

    /**
     * Returns an estimated token count for {@code object} using the default ({@code CL100K_BASE})
     * encoding. Returns 0 on any failure.
     */
    public int countTokens(Object object) {
        return countTokens(object, DEFAULT_ENCODING);
    }

    /**
     * Returns an estimated token count for {@code object} after JSON serialization, using the
     * given encoding. Falls back to the default encoding if {@code encodingType} cannot be
     * resolved, and returns 0 on any serialization/encoding failure.
     */
    public int countTokens(Object object, EncodingType encodingType) {
        if (object == null) {
            return 0;
        }
        try {
            String json = objectMapper.writeValueAsString(object);
            return resolveEncoding(encodingType).countTokens(json);
        } catch (Exception e) {
            log.debug("[TOKEN-ESTIMATOR] countTokens failed for {}: {}", objectClass(object), e.getMessage());
            return 0;
        }
    }

    /**
     * Returns the UTF-8 byte size of {@code object} after JSON serialization.
     * Returns 0 on any failure so callers are never blocked.
     */
    public int countBytes(Object object) {
        if (object == null) {
            return 0;
        }
        try {
            return objectMapper.writeValueAsBytes(object).length;
        } catch (Exception e) {
            log.debug("[TOKEN-ESTIMATOR] countBytes failed for {}: {}", objectClass(object), e.getMessage());
            return 0;
        }
    }

    private Encoding resolveEncoding(EncodingType type) {
        EncodingType effective = type != null ? type : DEFAULT_ENCODING;
        return encodingCache.computeIfAbsent(effective, t -> {
            try {
                return registry.getEncoding(t);
            } catch (Exception e) {
                log.debug("[TOKEN-ESTIMATOR] Unknown encoding {} — falling back to {}", t, DEFAULT_ENCODING);
                return registry.getEncoding(DEFAULT_ENCODING);
            }
        });
    }

    private static String objectClass(Object obj) {
        return obj == null ? "null" : obj.getClass().getSimpleName();
    }
}
