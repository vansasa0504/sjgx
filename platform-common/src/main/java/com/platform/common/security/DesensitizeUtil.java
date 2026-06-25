package com.platform.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class DesensitizeUtil {
    private DesensitizeUtil() {
    }

    public static String mask(String value, int prefix, int suffix) {
        if (value == null || value.length() <= prefix + suffix) {
            return "****";
        }
        return value.substring(0, prefix) + "****" + value.substring(value.length() - suffix);
    }

    public static String replace(String value, String replacement) {
        return value == null ? null : replacement;
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("hash failed", ex);
        }
    }
}
