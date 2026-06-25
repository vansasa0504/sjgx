package com.platform.gateway;

import com.platform.common.auth.AuthPrincipal;
import com.platform.common.auth.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtHeaderFilter implements GlobalFilter, Ordered {
    private final JwtUtil jwtUtil;

    public JwtHeaderFilter() {
        this(new JwtUtil(System.getenv().getOrDefault("JWT_SECRET", "change-me-in-env"), Clock.systemUTC()));
    }

    JwtHeaderFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, String> headers = enrichHeaders(exchange.getRequest().getHeaders().getFirst("Authorization"));
        if (headers.isEmpty()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest.Builder request = exchange.getRequest().mutate();
        headers.forEach(request::header);
        return chain.filter(exchange.mutate().request(request.build()).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    Map<String, String> enrichHeaders(String authorizationHeader) {
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
