package com.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.auth.JwtUtil;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 鉴权公共配置。所有业务模块通过 {@code scanBasePackages} 扫描 {@code com.platform.common}
 * 自动获得 JWT 过滤器与权限切面。
 */
@Configuration
public class CommonSecurityConfiguration {

    @Bean
    public JwtUtil jwtUtil(@Value("${security.jwt.secret}") String secret) {
        return new JwtUtil(secret, Clock.systemUTC());
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        return new JwtAuthFilter(jwtUtil, objectMapper);
    }

    @Bean
    public RequirePermissionAspect requirePermissionAspect() {
        return new RequirePermissionAspect();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("jwtAuthFilter");
        return registration;
    }
}
