package com.platform.auth;

import java.net.URI;

public interface SsoAdapter {
    URI redirect(String state);
    SsoIdentity callback(String code, String state);
    record SsoIdentity(String username) {}
}
