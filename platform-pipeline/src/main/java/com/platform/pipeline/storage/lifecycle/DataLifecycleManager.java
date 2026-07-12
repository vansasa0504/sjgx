package com.platform.pipeline.storage.lifecycle;

import com.platform.common.db.IdGenerator;
import com.platform.pipeline.storage.etl.DataAsset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

public class DataLifecycleManager {
    private final LifecyclePolicy policy;
    private final Clock clock;
    private final List<LifecycleEvent> events = new ArrayList<>();
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public DataLifecycleManager(LifecyclePolicy policy) {
        this(policy, Clock.systemUTC());
    }

    public DataLifecycleManager(LifecyclePolicy policy, Clock clock) {
        this(policy, clock, null);
    }

    public DataLifecycleManager(LifecyclePolicy policy, Clock clock, JdbcTemplate jdbcTemplate) {
        this.policy = policy;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate == null ? null : new IdGenerator(jdbcTemplate);
    }

    public LifecycleAction scan(DataAsset asset) {
        return scan(asset, "system", "policy-scan", asset.assetCode());
    }

    public LifecycleAction scan(DataAsset asset, String operator, String reason, String objectKey) {
        Duration age = Duration.between(asset.createdAt(), clock.instant());
        LifecycleAction action = LifecycleAction.KEEP;
        if (age.compareTo(policy.destroyAfter()) >= 0) {
            action = LifecycleAction.DESTROY;
        } else if (age.compareTo(policy.archiveAfter()) >= 0) {
            action = LifecycleAction.ARCHIVE;
        }
        Instant operatedAt = Instant.now(clock);
        String proofHash = action == LifecycleAction.DESTROY
                ? proofHash(asset.assetCode(), action, operator, reason, operatedAt, objectKey)
                : null;
        LifecycleEvent event = new LifecycleEvent(asset.assetCode(), action, operatedAt, operator, reason, proofHash, objectKey);
        events.add(event);
        persist(event);
        return action;
    }

    public List<LifecycleEvent> events() {
        return List.copyOf(events);
    }

    public static String proofHash(String assetCode, LifecycleAction action, String operator, String reason,
                                   Instant operatedAt, String objectKey) {
        String canonical = lengthPrefixed(assetCode)
                + lengthPrefixed(String.valueOf(action))
                + lengthPrefixed(operator)
                + lengthPrefixed(reason)
                + lengthPrefixed(Objects.requireNonNull(operatedAt, "operatedAt").toString())
                + lengthPrefixed(objectKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String lengthPrefixed(String value) {
        String normalized = Objects.requireNonNullElse(value, "");
        return normalized.getBytes(StandardCharsets.UTF_8).length + ":" + normalized;
    }

    private void persist(LifecycleEvent event) {
        if (jdbcTemplate == null) {
            return;
        }
        long id = idGenerator.nextId("t_lifecycle_record");
        jdbcTemplate.update("""
                INSERT INTO t_lifecycle_record
                (id, asset_code, action, operated_at, operator, reason, proof_hash, object_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, event.assetCode(), event.action().name(), Timestamp.from(event.operatedAt()),
                event.operator(), event.reason(), event.proofHash(), event.objectKey());
    }
}
