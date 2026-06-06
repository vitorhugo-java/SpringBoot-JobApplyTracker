package com.jobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Estimates token counts and serialized byte sizes for arbitrary objects.
 * Uses cl100k_base (GPT-4 / Claude-compatible) encoding via JTokkit.
 * This is a cost-ranking heuristic — not a billing-accurate counter.
 */
@Service
public class TokenEstimatorService {

    private static final Logger log = LoggerFactory.getLogger(TokenEstimatorService.class);

    private final ObjectMapper objectMapper;
    private final Encoding encoding;

    public TokenEstimatorService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * Returns an estimated token count for {@code object} after JSON serialization.
     * Returns 0 on any failure so callers are never blocked.
     */
    public int countTokens(Object object) {
        if (object == null) {
            return 0;
        }
        try {
            String json = objectMapper.writeValueAsString(object);
            return encoding.countTokens(json);
        } catch (Exception e) {
            log.warn("[TOKEN-ESTIMATOR] countTokens failed for {}: {}", objectClass(object), e.getMessage());
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
            log.warn("[TOKEN-ESTIMATOR] countBytes failed for {}: {}", objectClass(object), e.getMessage());
            return 0;
        }
    }

    private static String objectClass(Object obj) {
        return obj == null ? "null" : obj.getClass().getSimpleName();
    }
}
