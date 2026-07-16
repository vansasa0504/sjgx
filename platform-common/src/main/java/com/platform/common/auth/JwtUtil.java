package com.platform.common.auth;

import com.platform.common.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

public class JwtUtil {
    private final String secret;
    private final Clock clock;

    public JwtUtil(String secret, Clock clock) {
        if (secret == null || secret.isBlank() || "change-me-in-env".equals(secret)) {
            throw new IllegalStateException("security.jwt.secret must be configured securely");
        }
        this.secret = secret;
        this.clock = clock;
    }

    public String issue(String username, Set<String> permissions, long ttlSeconds) {
        long expiresAt = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = username + "|" + expiresAt + "|" + String.join(",", permissions);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public AuthPrincipal parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
            throw new BusinessException("AUTH-401", "invalid token");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] fields = payload.split("\\|", -1);
        long expiresAt = Long.parseLong(fields[1]);
        if (expiresAt <= clock.instant().getEpochSecond()) {
            throw new BusinessException("AUTH-401", "expired token");
        }
        Set<String> permissions = fields[2].isBlank() ? Set.of() : Set.of(fields[2].split(","));
        return new AuthPrincipal(fields[0], permissions, expiresAt);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("AUTH-500", "token signing failed");
        }
    }
}
