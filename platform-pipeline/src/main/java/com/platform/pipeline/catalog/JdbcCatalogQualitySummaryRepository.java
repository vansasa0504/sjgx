package com.platform.pipeline.catalog;

import com.platform.common.db.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcCatalogQualitySummaryRepository implements CatalogQualitySummaryRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcCatalogQualitySummaryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public CatalogQualitySummary upsert(long catalogId, double score, int issueCount) {
        Instant now = Instant.now();
        int affected = jdbcTemplate.update("""
                UPDATE t_catalog_quality_summary
                SET score = ?, issue_count = ?, updated_at = ?
                WHERE catalog_id = ?
                """, score, issueCount, Timestamp.from(now), catalogId);
        if (affected == 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_catalog_quality_summary
                    (id, catalog_id, score, issue_count, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, idGenerator.nextId("t_catalog_quality_summary"), catalogId, score,
                    issueCount, Timestamp.from(now));
        }
        return new CatalogQualitySummary(catalogId, score, issueCount, now);
    }

    @Override
    public Optional<CatalogQualitySummary> findByCatalogId(long catalogId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT catalog_id, score, issue_count, updated_at
                    FROM t_catalog_quality_summary
                    WHERE catalog_id = ?
                    """, (rs, rowNum) -> map(rs), catalogId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private CatalogQualitySummary map(ResultSet rs) throws SQLException {
        return new CatalogQualitySummary(rs.getLong("catalog_id"), rs.getDouble("score"),
                rs.getInt("issue_count"), rs.getTimestamp("updated_at").toInstant());
    }
}
