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

    @Test
    void usesRandomIvForCbcMode() {
        String first = Sm4Util.encrypt("same-plain", "local-test-key");
        String second = Sm4Util.encrypt("same-plain", "local-test-key");

        assertNotEquals(first, second);
        assertEquals("same-plain", Sm4Util.decrypt(first, "local-test-key"));
        assertEquals("same-plain", Sm4Util.decrypt(second, "local-test-key"));
    }
}
