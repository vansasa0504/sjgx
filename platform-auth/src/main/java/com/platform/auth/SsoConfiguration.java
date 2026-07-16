package com.platform.auth;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SsoConfiguration {
    private static final Set<String> MOCK_ALLOWED_PROFILES = Set.of("test", "dev");

    @Bean
    SsoAdapter ssoAdapter(@Value("${security.sso.mock-enabled:false}") boolean mockEnabled,
            @Value("${security.sso.issuer:https://mock-sso.invalid}") String issuer,
            @Value("${security.sso.client-id:placeholder}") String clientId,
            Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean mockAllowed = activeProfiles.length > 0
                && Arrays.stream(activeProfiles).allMatch(MOCK_ALLOWED_PROFILES::contains);
        if (mockEnabled && !mockAllowed) {
            throw new IllegalStateException("mock SSO may only be enabled in test or dev profiles");
        }
        return mockEnabled ? new MockSsoAdapter(issuer, clientId) : new DisabledSsoAdapter();
    }
}
