package com.platform.gateway;

import com.platform.common.auth.JwtUtil;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtHeaderFilterTest {
    @Test
    void enrichesUserHeadersFromBearerToken() {
        JwtUtil jwtUtil = new JwtUtil("secret", Clock.systemUTC());
        String token = jwtUtil.issue("admin", Set.of("partner:create"), 60);
        JwtHeaderFilter filter = new JwtHeaderFilter(jwtUtil);

        Map<String, String> headers = filter.enrichHeaders("Bearer " + token);

        assertEquals("admin", headers.get("X-User-Name"));
        assertTrue(headers.get("X-User-Permissions").contains("partner:create"));
    }

    @Test
    void ignoresMissingToken() {
        JwtHeaderFilter filter = new JwtHeaderFilter(new JwtUtil("secret", Clock.systemUTC()));

        assertTrue(filter.enrichHeaders(null).isEmpty());
    }
}
