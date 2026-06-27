package com.platform.auth;

import java.util.Set;

public record Role(String name, Set<String> permissions) {
}
