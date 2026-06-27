package com.platform.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.auth.AuthPrincipal;
import com.platform.common.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthFilterTest {
    private final JwtUtil jwtUtil = new JwtUtil("test-secret", Clock.systemUTC());
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtUtil, new ObjectMapper());

    @AfterEach
    void clear() {
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void validTokenSetsPrincipalAndContinues() throws Exception {
        String token = jwtUtil.issue("alice", Set.of("partner:view"), 60);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/partners");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] continued = {false};
        FilterChain chain = (req, res) -> continued[0] = true;

        filter.doFilter(request, response, chain);

        assertEquals(true, continued[0]);
        assertNotNull(request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR));
        AuthPrincipal principal = (AuthPrincipal) request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        assertEquals("alice", principal.username());
    }

    @Test
    void missingTokenRejectsWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/partners");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] continued = {false};
        FilterChain chain = (req, res) -> continued[0] = true;

        filter.doFilter(request, response, chain);

        assertEquals(false, continued[0]);
        assertEquals(401, response.getStatus());
    }

    @Test
    void invalidTokenRejectsWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/partners");
        request.addHeader("Authorization", "Bearer garbage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals(401, response.getStatus());
    }

    @Test
    void whitelistedPathPassesWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] continued = {false};

        filter.doFilter(request, response, (req, res) -> continued[0] = true);

        assertEquals(true, continued[0]);
        assertNull(request.getAttribute(JwtAuthFilter.PRINCIPAL_ATTR));
    }

    @Test
    void healthEndpointPassesWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] continued = {false};

        filter.doFilter(request, response, (req, res) -> continued[0] = true);

        assertEquals(true, continued[0]);
    }

    @Test
    void metricsEndpointRequiresToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertEquals(401, response.getStatus());
    }
}
