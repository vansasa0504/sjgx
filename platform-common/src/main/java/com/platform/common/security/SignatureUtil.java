package com.platform.common.security;

import com.platform.common.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SignatureUtil {
    private static final long WINDOW_SECONDS = 300;
    private final Clock clock;
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet();

    public SignatureUtil(Clock clock) {
        this.clock = clock;
    }

    public String sign(String apiKey, String secret, long timestamp, String nonce, String body) {
        return hmac(secret, canonical(apiKey, timestamp, nonce, body));
    }

    public void verify(String apiKey, String secret, long timestamp, String nonce, String body, String signature) {
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > WINDOW_SECONDS) {
            throw new BusinessException("AUTH-408", "timestamp expired");
        }
        String replayKey = apiKey + ':' + nonce;
        if (!usedNonces.add(replayKey)) {
            throw new BusinessException("AUTH-409", "replay request");
        }
        String expected = sign(apiKey, secret, timestamp, nonce, body);
        if (!constantTimeEquals(expected, signature)) {
            throw new BusinessException("AUTH-403", "bad signature");
        }
    }

    private String canonical(String apiKey, long timestamp, String nonce, String body) {
        return apiKey + '\n' + timestamp + '\n' + nonce + '\n' + (body == null ? "" : body);
    }

    private String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("AUTH-500", "signature failed");
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < left.length(); i++) {
            diff |= left.charAt(i) ^ right.charAt(i);
        }
        return diff == 0;
    }
}
