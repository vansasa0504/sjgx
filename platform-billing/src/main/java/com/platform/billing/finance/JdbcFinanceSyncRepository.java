package com.platform.billing.finance;

import com.platform.common.db.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcFinanceSyncRepository implements FinanceSyncRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcFinanceSyncRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public FinanceSyncRecord save(FinanceSyncRecord record) {
        long id = idGenerator.nextId("t_finance_sync_record");
        Instant syncedAt = record.syncedAt() == null ? Instant.now() : record.syncedAt();
        jdbcTemplate.update("""
                INSERT INTO t_finance_sync_record
                (id, bill_no, adapter_type, external_no, status, retry_count, message, synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, record.billNo(), record.adapterType(), record.externalNo(), record.status(),
                record.retryCount(), record.message(), Timestamp.from(syncedAt));
        return new FinanceSyncRecord(id, record.billNo(), record.adapterType(), record.externalNo(),
                record.status(), record.retryCount(), record.message(), syncedAt);
    }

    @Override
    public Optional<FinanceSyncRecord> findLastFailed(String billNo, String adapterType) {
        List<FinanceSyncRecord> records = jdbcTemplate.query("""
                SELECT id, bill_no, adapter_type, external_no, status, retry_count, message, synced_at
                FROM t_finance_sync_record
                WHERE bill_no = ? AND adapter_type = ? AND status = ?
                ORDER BY synced_at DESC, id DESC LIMIT 1
                """, (rs, rowNum) -> map(rs), billNo, adapterType, "FAILED");
        return records.isEmpty() ? Optional.empty() : Optional.of(records.get(0));
    }

    @Override
    public List<FinanceSyncRecord> findByBillNo(String billNo) {
        return jdbcTemplate.query("""
                SELECT id, bill_no, adapter_type, external_no, status, retry_count, message, synced_at
                FROM t_finance_sync_record
                WHERE bill_no = ?
                ORDER BY synced_at, id
                """, (rs, rowNum) -> map(rs), billNo);
    }

    private FinanceSyncRecord map(ResultSet rs) throws SQLException {
        return new FinanceSyncRecord(
                rs.getLong("id"),
                rs.getString("bill_no"),
                rs.getString("adapter_type"),
                rs.getString("external_no"),
                rs.getString("status"),
                rs.getInt("retry_count"),
                rs.getString("message"),
                rs.getTimestamp("synced_at").toInstant());
    }
}
