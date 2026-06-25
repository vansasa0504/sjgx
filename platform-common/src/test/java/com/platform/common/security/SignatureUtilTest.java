package com.platform.common.security;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignatureUtilTest {
    @Test
    void verifiesCorrectSignatureAndRejectsBadCases() {
        SignatureUtil verifier = new SignatureUtil(Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC));
        long timestamp = Instant.parse("2026-06-25T00:00:00Z").getEpochSecond();
        String signature = verifier.sign("api-key", "secret", timestamp, "nonce-1", "body");

        assertDoesNotThrow(() -> verifier.verify("api-key", "secret", timestamp, "nonce-1", "body", signature));
        assertThrows(BusinessException.class, () -> verifier.verify("api-key", "secret", timestamp, "nonce-2", "body", "bad"));
        assertThrows(BusinessException.class, () -> verifier.verify("api-key", "secret", timestamp - 301, "nonce-3", "body", signature));
        assertThrows(BusinessException.class, () -> verifier.verify("api-key", "secret", timestamp, "nonce-1", "body", signature));
    }
}
