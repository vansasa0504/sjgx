package com.platform.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XssFilterTest {
    @Test
    void sanitizesHtmlControlCharacters() {
        assertEquals("&lt;script&gt;alert(&#x27;x&#x27;)&lt;/script&gt;", XssFilter.sanitize("<script>alert('x')</script>"));
    }
}
