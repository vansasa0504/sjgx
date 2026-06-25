package com.platform.gateway;

import com.platform.auth.AuthPrincipal;
import com.platform.auth.JwtUtil;

import java.util.HashMap;
import java.util.Map;

public class JwtHeaderFilter {
    private final JwtUtil jwtUtil;

    public JwtHeaderFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public Map<String, String> enrichHeaders(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Map.of();
        }
        AuthPrincipal principal = jwtUtil.parse(authorizationHeader.substring("Bearer ".length()));
        Map<String, String> headers = new HashMap<>();
        headers.put("X-User-Name", principal.username());
        headers.put("X-User-Permissions", String.join(",", principal.permissions()));
        return headers;
    }
}
