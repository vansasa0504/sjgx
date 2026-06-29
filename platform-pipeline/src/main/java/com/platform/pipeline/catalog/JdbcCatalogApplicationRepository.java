package com.platform.pipeline.catalog;

import com.platform.common.db.IdGenerator;
import com.platform.common.exception.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcCatalogApplicationRepository implements CatalogApplicationRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcCatalogApplicationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public CatalogApplication create(long catalogId, String applicant, String reason, String scope) {
        long id = idGenerator.nextId("t_catalog_application");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO t_catalog_application
                            (id, catalog_id, applicant, reason, scope, status, approver, created_at, approved_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, catalogId, applicant, reason, scope, CatalogApplication.PENDING, null,
                Timestamp.from(now), null);
        return new CatalogApplication(id, catalogId, applicant, reason, scope, CatalogApplication.PENDING,
                null, now, null);
    }

    @Override
    public CatalogApplication approve(long id, String approver) {
        return transit(id, approver, CatalogApplication.APPROVED);
    }

    @Override
    public CatalogApplication reject(long id, String approver) {
        return transit(id, approver, CatalogApplication.REJECTED);
    }

    @Override
    public Optional<CatalogApplication> findById(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT id, catalog_id, applicant, reason, scope, status, approver, created_at, approved_at FROM t_catalog_application WHERE id = ?",
                    (rs, rowNum) -> map(rs), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<CatalogApplication> findByApplicant(String applicant) {
        return jdbcTemplate.query(
                "SELECT id, catalog_id, applicant, reason, scope, status, approver, created_at, approved_at FROM t_catalog_application WHERE applicant = ? ORDER BY id",
                (rs, rowNum) -> map(rs), applicant);
    }

    @Override
    public boolean hasApproved(long catalogId, String applicant) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_catalog_application WHERE catalog_id = ? AND applicant = ? AND status = ?",
                Integer.class, catalogId, applicant, CatalogApplication.APPROVED);
        return count != null && count > 0;
    }

    @Override
    public long countByCatalog(long catalogId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_catalog_application WHERE catalog_id = ?",
                Long.class, catalogId);
        return count == null ? 0L : count;
    }

    private CatalogApplication transit(long id, String approver, String targetStatus) {
        CatalogApplication current = findById(id)
                .orElseThrow(() -> new BusinessException("CATALOG_APP-404", "application not found"));
        if (!CatalogApplication.PENDING.equals(current.status())) {
            throw new BusinessException("CATALOG_APP-409", "application already reviewed");
        }
        Instant approvedAt = Instant.now();
        int updated = jdbcTemplate.update("""
                        UPDATE t_catalog_application
                        SET status = ?, approver = ?, approved_at = ?
                        WHERE id = ? AND status = ?
                        """,
                targetStatus, approver, Timestamp.from(approvedAt), id, CatalogApplication.PENDING);
        if (updated == 0) {
            throw new BusinessException("CATALOG_APP-409", "application already reviewed");
        }
        return new CatalogApplication(current.id(), current.catalogId(), current.applicant(),
                current.reason(), current.scope(), targetStatus, approver, current.createdAt(), approvedAt);
    }

    private CatalogApplication map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        return new CatalogApplication(
                rs.getLong("id"),
                rs.getLong("catalog_id"),
                rs.getString("applicant"),
                rs.getString("reason"),
                rs.getString("scope"),
                rs.getString("status"),
                rs.getString("approver"),
                rs.getTimestamp("created_at").toInstant(),
                approvedAt == null ? null : approvedAt.toInstant());
    }
}
