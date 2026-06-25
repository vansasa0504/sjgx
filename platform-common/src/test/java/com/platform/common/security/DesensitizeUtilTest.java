package com.platform.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DesensitizeUtilTest {
    @Test
    void masksValueWithVisibleEdges() {
        assertEquals("138****5678", DesensitizeUtil.mask("13812345678", 3, 4));
    }

    @Test
    void replacesValue() {
        assertEquals("[hidden]", DesensitizeUtil.replace("secret", "[hidden]"));
    }

    @Test
    void hashesValue() {
        assertNotEquals("secret", DesensitizeUtil.hash("secret"));
        assertEquals(64, DesensitizeUtil.hash("secret").length());
    }
}
