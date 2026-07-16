package com.platform.auth;

import com.platform.common.auth.JwtUtil;
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
    AuthService authService(JwtUtil jwtUtil,
                            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        return new AuthService(jwtUtil, jdbcTemplate);
    }
}
