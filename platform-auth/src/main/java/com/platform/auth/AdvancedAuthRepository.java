package com.platform.auth;

import com.platform.common.db.IdGenerator;
import com.platform.common.exception.BusinessException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persistent MFA and certificate state, with an in-memory test fallback. */
public class AdvancedAuthRepository {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final Map<String, MfaState> fallbackMfa = new HashMap<>();
    private final Map<String, CertificateState> fallbackCerts = new HashMap<>();

    public AdvancedAuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.ids = jdbc == null ? null : new IdGenerator(jdbc);
    }

    public synchronized MfaState mfa(String username) {
        if (jdbc == null) return fallbackMfa.get(username);
        List<MfaState> rows = jdbc.query("""
                SELECT mfa_secret_cipher, mfa_enabled, mfa_last_counter
                FROM t_user WHERE username = ?
                """, (rs, n) -> new MfaState(rs.getString(1), rs.getBoolean(2), rs.getLong(3)), username);
        return rows.isEmpty() || rows.get(0).secretCipher() == null ? null : rows.get(0);
    }

    public synchronized void saveMfa(String username, MfaState state) {
        if (jdbc == null) {
            fallbackMfa.put(username, state);
            return;
        }
        int updated = jdbc.update("""
                UPDATE t_user SET mfa_secret_cipher = ?, mfa_enabled = ?, mfa_last_counter = ?,
                updated_at = CURRENT_TIMESTAMP WHERE username = ?
                """, state.secretCipher(), state.enabled(), state.lastCounter(), username);
        if (updated != 1) throw new BusinessException("AUTH-401", "user not found");
    }

    public synchronized void clearMfa(String username) {
        if (jdbc == null) fallbackMfa.remove(username);
        else jdbc.update("""
                UPDATE t_user SET mfa_secret_cipher = NULL, mfa_enabled = 0, mfa_last_counter = -1,
                updated_at = CURRENT_TIMESTAMP WHERE username = ?
                """, username);
    }

    /** Atomically advances the replay counter. */
    public synchronized boolean advanceCounter(String username, long counter) {
        if (jdbc == null) {
            MfaState current = fallbackMfa.get(username);
            if (current == null || counter <= current.lastCounter()) return false;
            fallbackMfa.put(username, new MfaState(current.secretCipher(), current.enabled(), counter));
            return true;
        }
        return jdbc.update("""
                UPDATE t_user SET mfa_last_counter = ?, updated_at = CURRENT_TIMESTAMP
                WHERE username = ? AND mfa_enabled = 1 AND mfa_last_counter < ?
                """, counter, username, counter) == 1;
    }

    public synchronized void bindCertificate(CertificateState state) {
        if (jdbc == null) {
            fallbackCerts.put(state.fingerprint(), state);
            return;
        }
        Long userId = userId(state.username());
        jdbc.update("""
                INSERT INTO t_user_certificate
                (id, user_id, fingerprint_sha256, subject_cn, serial_number, certificate_cipher,
                 status, not_before, not_after, rotated_from, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ids.nextId("t_user_certificate"), userId, state.fingerprint(), state.subjectCn(),
                state.serialNumber(), state.certificateCipher(), state.status(), state.notBefore(), state.notAfter());
    }

    public synchronized CertificateState certificate(String fingerprint) {
        if (jdbc == null) return fallbackCerts.get(fingerprint);
        List<CertificateState> rows = jdbc.query("""
                SELECT c.fingerprint_sha256, u.username, c.subject_cn, c.serial_number,
                       c.certificate_cipher, c.status, c.not_before, c.not_after
                FROM t_user_certificate c JOIN t_user u ON u.id = c.user_id
                WHERE c.fingerprint_sha256 = ?
                """, (rs, n) -> new CertificateState(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getTimestamp(7).toInstant(), rs.getTimestamp(8).toInstant()), fingerprint);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public synchronized void revokeCertificate(String fingerprint) {
        if (jdbc == null) {
            CertificateState current = fallbackCerts.get(fingerprint);
            if (current != null) fallbackCerts.put(fingerprint, current.withStatus("REVOKED"));
        } else {
            jdbc.update("UPDATE t_user_certificate SET status = 'REVOKED', updated_at = CURRENT_TIMESTAMP WHERE fingerprint_sha256 = ?",
                    fingerprint);
        }
    }

    public Set<String> permissions(String username) {
        if (jdbc == null) return Set.of();
        return new HashSet<>(jdbc.queryForList("""
                SELECT p.permission_code FROM t_user_permission p
                JOIN t_user u ON u.id = p.user_id WHERE u.username = ?
                """, String.class, username));
    }

    private Long userId(String username) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM t_user WHERE username = ?", Long.class, username);
        if (ids.isEmpty()) throw new BusinessException("AUTH-401", "user not found");
        return ids.get(0);
    }

    public record MfaState(String secretCipher, boolean enabled, long lastCounter) {}
    public record CertificateState(String fingerprint, String username, String subjectCn, String serialNumber,
            String certificateCipher, String status, Instant notBefore, Instant notAfter) {
        CertificateState withStatus(String next) {
            return new CertificateState(fingerprint, username, subjectCn, serialNumber,
                    certificateCipher, next, notBefore, notAfter);
        }
    }
}
