package com.platform.billing.report;

import com.platform.common.db.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcRegulatoryReportRepository implements RegulatoryReportRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcRegulatoryReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public RegulatoryReportRecord save(RegulatoryReportRecord record) {
        long id = idGenerator.nextId("t_regulatory_report");
        Instant generatedAt = record.generatedAt() == null ? Instant.now() : record.generatedAt();
        jdbcTemplate.update("""
                INSERT INTO t_regulatory_report
                (id, report_type, period_from, period_to, content, status, receipt_no, receipt_message,
                 generated_at, submitted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, record.reportType(), ts(record.periodFrom()), ts(record.periodTo()), record.content(),
                record.status(), record.receiptNo(), record.receiptMessage(), Timestamp.from(generatedAt),
                ts(record.submittedAt()));
        return new RegulatoryReportRecord(id, record.reportType(), record.periodFrom(), record.periodTo(),
                record.content(), record.status(), record.receiptNo(), record.receiptMessage(), generatedAt,
                record.submittedAt());
    }

    @Override
    public RegulatoryReportRecord updateSubmission(long id, String status, String receiptNo, String receiptMessage) {
        Instant submittedAt = Instant.now();
        jdbcTemplate.update("""
                UPDATE t_regulatory_report
                SET status = ?, receipt_no = ?, receipt_message = ?, submitted_at = ?
                WHERE id = ?
                """, status, receiptNo, receiptMessage, Timestamp.from(submittedAt), id);
        return findById(id).orElse(null);
    }

    @Override
    public Optional<RegulatoryReportRecord> findById(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, report_type, period_from, period_to, content, status, receipt_no, receipt_message,
                           generated_at, submitted_at
                    FROM t_regulatory_report WHERE id = ?
                    """, (rs, rowNum) -> map(rs), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<RegulatoryReportRecord> findByType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return jdbcTemplate.query("""
                    SELECT id, report_type, period_from, period_to, content, status, receipt_no, receipt_message,
                           generated_at, submitted_at
                    FROM t_regulatory_report ORDER BY generated_at, id
                    """, (rs, rowNum) -> map(rs));
        }
        return jdbcTemplate.query("""
                SELECT id, report_type, period_from, period_to, content, status, receipt_no, receipt_message,
                       generated_at, submitted_at
                FROM t_regulatory_report WHERE report_type = ? ORDER BY generated_at, id
                """, (rs, rowNum) -> map(rs), reportType);
    }

    private RegulatoryReportRecord map(ResultSet rs) throws SQLException {
        return new RegulatoryReportRecord(
                rs.getLong("id"),
                rs.getString("report_type"),
                instant(rs.getTimestamp("period_from")),
                instant(rs.getTimestamp("period_to")),
                rs.getString("content"),
                rs.getString("status"),
                rs.getString("receipt_no"),
                rs.getString("receipt_message"),
                instant(rs.getTimestamp("generated_at")),
                instant(rs.getTimestamp("submitted_at")));
    }

    private Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
