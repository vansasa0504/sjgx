package com.platform.common.auth;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {
    @Test
    void parsesIssuedToken() {
        JwtUtil jwtUtil = new JwtUtil("secret", Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneOffset.UTC));
        String token = jwtUtil.issue("admin", Set.of("partner:create"), 60);

        AuthPrincipal principal = jwtUtil.parse(token);

        assertEquals("admin", principal.username());
        assertEquals(true, principal.hasPermission("partner:create"));
    }

    @Test
    void rejectsTamperedToken() {
        JwtUtil jwtUtil = new JwtUtil("secret", Clock.systemUTC());
        String token = jwtUtil.issue("admin", Set.of(), 60);

        assertThrows(BusinessException.class, () -> jwtUtil.parse(token + "bad"));
    }
}
