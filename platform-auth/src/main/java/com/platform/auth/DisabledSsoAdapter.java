package com.platform.auth;

import com.platform.common.exception.BusinessException;
import java.net.URI;

/** Production-safe placeholder until the institution supplies its IAM/SSO adapter. */
public class DisabledSsoAdapter implements SsoAdapter {
    @Override public URI redirect(String state) {
        throw new BusinessException("AUTH-503", "SSO adapter not configured");
    }
    @Override public SsoIdentity callback(String code, String state) {
        throw new BusinessException("AUTH-401", "SSO callback unavailable");
    }
}
