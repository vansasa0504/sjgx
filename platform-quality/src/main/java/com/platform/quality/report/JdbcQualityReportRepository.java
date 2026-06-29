package com.platform.quality.report;

import com.platform.common.db.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcQualityReportRepository implements QualityReportRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcQualityReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public QualityReportRecord save(QualityReportRecord record) {
        long id = idGenerator.nextId("t_quality_report");
        Instant generatedAt = record.generatedAt() == null ? Instant.now() : record.generatedAt();
        jdbcTemplate.update("""
                INSERT INTO t_quality_report
                (id, dimension, dimension_value, check_count, pass_count, fail_count, fail_rate, score, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, record.dimension(), record.dimensionValue(), record.checkCount(),
                record.passCount(), record.failCount(), record.failRate(), record.score(),
                Timestamp.from(generatedAt));
        return new QualityReportRecord(id, record.dimension(), record.dimensionValue(), record.checkCount(),
                record.passCount(), record.failCount(), record.failRate(), record.score(), generatedAt);
    }

    @Override
    public Optional<QualityReportRecord> findById(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, dimension, dimension_value, check_count, pass_count, fail_count, fail_rate, score, generated_at
                    FROM t_quality_report WHERE id = ?
                    """, (rs, rowNum) -> map(rs), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<QualityReportRecord> findByDimension(String dimension) {
        if (dimension == null) {
            return jdbcTemplate.query("""
                    SELECT id, dimension, dimension_value, check_count, pass_count, fail_count, fail_rate, score, generated_at
                    FROM t_quality_report ORDER BY id
                    """, (rs, rowNum) -> map(rs));
        }
        return jdbcTemplate.query("""
                SELECT id, dimension, dimension_value, check_count, pass_count, fail_count, fail_rate, score, generated_at
                FROM t_quality_report WHERE dimension = ? ORDER BY id
                """, (rs, rowNum) -> map(rs), dimension);
    }

    private QualityReportRecord map(ResultSet rs) throws SQLException {
        return new QualityReportRecord(
                rs.getLong("id"),
                rs.getString("dimension"),
                rs.getString("dimension_value"),
                rs.getInt("check_count"),
                rs.getInt("pass_count"),
                rs.getInt("fail_count"),
                rs.getDouble("fail_rate"),
                rs.getDouble("score"),
                rs.getTimestamp("generated_at").toInstant());
    }
}
