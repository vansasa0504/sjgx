package com.platform.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = "com.platform")
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    AuthService authService() {
        return new AuthService(System.getenv().getOrDefault("JWT_SECRET", "change-me-in-env"), Clock.systemUTC());
    }
}
