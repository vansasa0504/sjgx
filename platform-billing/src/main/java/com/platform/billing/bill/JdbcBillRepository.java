package com.platform.billing.bill;

import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcBillRepository implements BillRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final BillItemRepository itemRepository;

    public JdbcBillRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    public JdbcBillRepository(JdbcTemplate jdbcTemplate, BillItemRepository itemRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
        this.itemRepository = itemRepository;
    }

    @Override
    public Bill save(Bill bill) {
        Long id = bill.id();
        if (id == null) {
            id = idGenerator.nextId("t_bill");
        }
        Instant now = Instant.now();
        Instant createdAt = bill.createdAt() != null ? bill.createdAt() : now;
        Instant updatedAt = bill.updatedAt() != null ? bill.updatedAt() : now;
        int affected = jdbcTemplate.update("""
                UPDATE t_bill SET bill_type=?, bill_period=?, period_start=?, period_end=?,
                    total_amount=?, status=?, updated_at=? WHERE id=?
                """,
                bill.billType().name(), bill.billPeriod().name(), bill.periodStart(), bill.periodEnd(),
                bill.totalAmount(), bill.status().name(), Timestamp.from(updatedAt), id);
        if (affected == 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_bill (id, bill_no, bill_type, bill_period, period_start, period_end,
                        total_amount, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    id, bill.billNo(), bill.billType().name(), bill.billPeriod().name(),
                    bill.periodStart(), bill.periodEnd(), bill.totalAmount(), bill.status().name(),
                    Timestamp.from(createdAt), Timestamp.from(updatedAt));
        }
        return new Bill(id, bill.billNo(), bill.billType(), bill.billPeriod(), bill.periodStart(),
                bill.periodEnd(), bill.totalAmount(), bill.status(), createdAt, updatedAt, bill.items());
    }

    @Override
    public Optional<Bill> findByBillNo(String billNo) {
        List<Bill> bills = jdbcTemplate.query(
                "SELECT id, bill_no, bill_type, bill_period, period_start, period_end, total_amount, status, created_at, updated_at FROM t_bill WHERE bill_no = ?",
                (rs, rowNum) -> mapBill(rs), billNo);
        return bills.isEmpty() ? Optional.empty() : Optional.of(bills.get(0));
    }

    @Override
    public List<Bill> findAll() {
        return jdbcTemplate.query(
                "SELECT id, bill_no, bill_type, bill_period, period_start, period_end, total_amount, status, created_at, updated_at FROM t_bill ORDER BY id",
                (rs, rowNum) -> mapBill(rs));
    }

    private Bill mapBill(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Bill(
                rs.getLong("id"),
                rs.getString("bill_no"),
                BillType.valueOf(rs.getString("bill_type")),
                BillPeriod.valueOf(rs.getString("bill_period")),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getBigDecimal("total_amount"),
                BillStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null,
                itemRepository == null ? List.of() : itemRepository.findByBillNo(rs.getString("bill_no")));
    }
}
