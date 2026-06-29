package com.platform.common.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

final class AuditHashing {
    private AuditHashing() {
    }

    static String hash(String prevHash, AuditEvent event) {
        return sha256(Objects.requireNonNullElse(prevHash, "") + "\n" + canonical(event));
    }

    private static String canonical(AuditEvent event) {
        return String.join("|",
                event.eventType(),
                event.actorType(),
                event.actorId(),
                event.targetType(),
                event.targetId(),
                event.action(),
                event.detail(),
                event.status().name(),
                String.valueOf(event.createdAt().toEpochMilli()));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
