package com.platform.auth;

import com.platform.common.audit.AuditLogger;
import com.platform.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {
    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();
    private final JwtUtil jwtUtil;

    public AuthService(String jwtSecret, Clock clock) {
        this.jwtUtil = new JwtUtil(jwtSecret, clock);
        users.put("admin", new UserAccount("admin", hash("admin123"),
                Set.of("partner:create", "partner:read", "ingest:create", "ingest:run")));
    }

    public String login(String username, String password) {
        UserAccount account = users.get(username);
        if (account == null || !account.passwordHash().equals(hash(password))) {
            throw new BusinessException("AUTH-401", "bad credentials");
        }
        AuditLogger.record("login", username);
        return jwtUtil.issue(username, account.permissions(), 3600);
    }

    public String refresh(String token) {
        AuthPrincipal principal = jwtUtil.parse(token);
        UserAccount account = users.get(principal.username());
        if (account == null) {
            throw new BusinessException("AUTH-401", "user disabled");
        }
        return jwtUtil.issue(account.username(), account.permissions(), 3600);
    }

    public void requirePermission(String token, String permission) {
        AuthPrincipal principal = jwtUtil.parse(token);
        if (!principal.hasPermission(permission)) {
            throw new BusinessException("AUTH-403", "permission denied");
        }
    }

    public AuthPrincipal parse(String token) {
        return jwtUtil.parse(token);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
