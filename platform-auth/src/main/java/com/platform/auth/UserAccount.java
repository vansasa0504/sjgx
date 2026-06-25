package com.platform.auth;

import java.util.Set;

public record UserAccount(String username, String passwordHash, Set<String> permissions) {
}
