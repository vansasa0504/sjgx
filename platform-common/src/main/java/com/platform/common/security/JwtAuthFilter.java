package com.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.auth.AuthPrincipal;
import com.platform.common.auth.JwtUtil;
import com.platform.common.model.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 鉴权过滤器。放行登录、健康检查、服务调用(API Key)路径，其余 {@code /api/v1/**} 与
 * {@code /users|/roles|/permissions/**} 必须携带有效 Bearer token，解析后把 {@link AuthPrincipal}
 * 放入 request attribute（key 见 {@link #PRINCIPAL_ATTR}）供 Controller 与权限切面使用。
 * <p>actuator 仅放行 {@code /actuator/health}，metrics/info 等仍需鉴权，避免指标泄露。
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL_ATTR = "authPrincipal";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final List<String> WHITELIST = List.of(
            "/auth/**",
            "/actuator/health",
            "/actuator/health/**",
            "/api/v1/services/*/invoke"
    );

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isWhitelisted(path)) {
            chain.doFilter(request, response);
            return;
        }
        String token = bearer(request.getHeader("Authorization"));
        if (token == null) {
            reject(response, "missing token");
            return;
        }
        try {
            AuthPrincipal principal = jwtUtil.parse(token);
            request.setAttribute(PRINCIPAL_ATTR, principal);
        } catch (Exception ex) {
            reject(response, ex.getMessage() == null ? "invalid token" : ex.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : WHITELIST) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private String bearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        return authorization.startsWith("Bearer ") ? authorization.substring("Bearer ".length()) : authorization;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail("AUTH-401", message)));
    }
}
