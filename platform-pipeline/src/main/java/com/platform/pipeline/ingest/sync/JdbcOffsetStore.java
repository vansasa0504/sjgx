package com.platform.pipeline.ingest.sync;

import com.platform.common.db.IdGenerator;

import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcOffsetStore implements OffsetStore {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcOffsetStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public long get(String key) {
        Key parsed = parse(key);
        return jdbcTemplate.query("""
                SELECT offset_value FROM t_ingest_checkpoint
                WHERE task_id = ? AND connector_type = ?
                """, (rs, rowNum) -> rs.getLong("offset_value"), parsed.taskId(), parsed.connectorType())
                .stream()
                .findFirst()
                .orElse(0L);
    }

    @Override
    public void put(String key, long offset) {
        Key parsed = parse(key);
        long current = get(key);
        long next = Math.max(current, offset);
        int affected = jdbcTemplate.update("""
                UPDATE t_ingest_checkpoint
                SET offset_value = ?, checkpoint_json = ?, updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND connector_type = ?
                """, next, "{\"offset\":" + next + "}", parsed.taskId(), parsed.connectorType());
        if (affected == 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_ingest_checkpoint
                    (id, task_id, connector_type, offset_value, checkpoint_json, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, idGenerator.nextId("t_ingest_checkpoint"), parsed.taskId(), parsed.connectorType(),
                    next, "{\"offset\":" + next + "}");
        }
    }

    private Key parse(String key) {
        String[] parts = key.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("checkpoint key must be taskId:connectorType");
        }
        return new Key(Long.parseLong(parts[0]), parts[1].toUpperCase(Locale.ROOT));
    }

    private record Key(long taskId, String connectorType) {
    }
}
