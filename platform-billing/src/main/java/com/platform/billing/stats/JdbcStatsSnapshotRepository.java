package com.platform.billing.stats;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcStatsSnapshotRepository implements StatsSnapshotRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcStatsSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public StatsSnapshot save(StatsSnapshot snapshot) {
        Long id = snapshot.id();
        if (id == null) {
            id = idGenerator.nextId("t_stats_snapshot");
        }
        jdbcTemplate.update(
                "INSERT INTO t_stats_snapshot (id, metric_name, dimension, dimension_id, metric_value, snapshot_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, snapshot.metricName().name(), snapshot.dimension().name(), snapshot.dimensionId(),
                snapshot.metricValue(), Timestamp.from(snapshot.snapshotAt() != null ? snapshot.snapshotAt() : Instant.now()));
        return new StatsSnapshot(id, snapshot.metricName(), snapshot.dimension(), snapshot.dimensionId(),
                snapshot.metricValue(), snapshot.snapshotAt());
    }

    @Override
    public List<StatsSnapshot> findAll() {
        return jdbcTemplate.query(
                "SELECT id, metric_name, dimension, dimension_id, metric_value, snapshot_at FROM t_stats_snapshot",
                (rs, rowNum) -> new StatsSnapshot(
                        rs.getLong("id"),
                        MetricName.valueOf(rs.getString("metric_name")),
                        StatsDimension.valueOf(rs.getString("dimension")),
                        (Long) rs.getObject("dimension_id"),
                        rs.getBigDecimal("metric_value"),
                        rs.getTimestamp("snapshot_at") != null ? rs.getTimestamp("snapshot_at").toInstant() : null));
    }
}
