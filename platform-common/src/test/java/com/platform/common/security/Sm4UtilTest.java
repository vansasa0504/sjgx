package com.platform.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Sm4UtilTest {
    @Test
    void encryptAndDecryptAreSymmetric() {
        String encrypted = Sm4Util.encrypt("partner-secret", "local-test-key");

        assertNotEquals("partner-secret", encrypted);
        assertEquals("partner-secret", Sm4Util.decrypt(encrypted, "local-test-key"));
    }
}
