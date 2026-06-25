package com.platform.auth;

import java.util.Set;

public record AuthPrincipal(String username, Set<String> permissions, long expiresAt) {
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
