package com.platform.auth;

import com.platform.common.auth.JwtUtil;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.platform.auth", "com.platform.common"})
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    AuthService authService(@Autowired(required = false) JwtUtil jwtUtil,
                            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        JwtUtil util = jwtUtil != null ? jwtUtil
                : new JwtUtil(System.getenv().getOrDefault("JWT_SECRET", "change-me-in-env"), Clock.systemUTC());
        return new AuthService(util, jdbcTemplate);
    }
}