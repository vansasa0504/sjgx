package com.platform.auth;

import com.platform.common.exception.BusinessException;
import java.net.URI;

/** Explicitly enabled test/dev adapter. It never interprets an authorization code as a username. */
public class MockSsoAdapter implements SsoAdapter {
    private final String issuer;
    private final String clientId;

    public MockSsoAdapter(String issuer, String clientId) {
        this.issuer = issuer;
        this.clientId = clientId;
    }

    @Override
    public URI redirect(String state) {
        return URI.create(issuer + "/authorize?client_id=" + clientId + "&state=" + state);
    }

    @Override
    public SsoIdentity callback(String code, String state) {
        if (!"test-valid-code".equals(code) || state == null || state.isBlank()) {
            throw new BusinessException("AUTH-401", "invalid SSO callback");
        }
        return new SsoIdentity("sso-test-user");
    }
}