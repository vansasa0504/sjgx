package com.platform.billing.bill;

import com.platform.common.db.IdGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcBillItemRepository implements BillItemRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcBillItemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public List<BillItem> saveAll(String billNo, Long billId, List<BillItem> items) {
        jdbcTemplate.update("DELETE FROM t_bill_item WHERE bill_no = ?", billNo);
        Instant now = Instant.now();
        List<BillItem> saved = items.stream()
                .map(item -> new BillItem(item.id() == null ? idGenerator.nextId("t_bill_item") : item.id(),
                        billId, billNo, item.itemType(), item.refId(), item.quantity(), item.unitPrice(),
                        item.amount(), item.period(), item.serviceCode(), item.consumerCode(), item.partnerCode(),
                        item.createdAt() == null ? now : item.createdAt()))
                .toList();
        for (BillItem item : saved) {
            jdbcTemplate.update("""
                    INSERT INTO t_bill_item (id, bill_id, bill_no, item_type, ref_id, quantity, unit_price,
                        amount, period, service_code, consumer_code, partner_code, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    item.id(), item.billId(), item.billNo(), item.itemType(), item.refId(), item.quantity(),
                    item.unitPrice(), item.amount(), item.period(), item.serviceCode(), item.consumerCode(),
                    item.partnerCode(), Timestamp.from(item.createdAt()));
        }
        return saved;
    }

    @Override
    public List<BillItem> findByBillNo(String billNo) {
        return jdbcTemplate.query("SELECT * FROM t_bill_item WHERE bill_no = ? ORDER BY id", this::mapItem, billNo);
    }

    @Override
    public List<BillItem> findAll() {
        return jdbcTemplate.query("SELECT * FROM t_bill_item ORDER BY id", this::mapItem);
    }

    private BillItem mapItem(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new BillItem(rs.getLong("id"), rs.getLong("bill_id"), rs.getString("bill_no"),
                rs.getString("item_type"), rs.getString("ref_id"), rs.getLong("quantity"),
                rs.getBigDecimal("unit_price"), rs.getBigDecimal("amount"), rs.getString("period"),
                rs.getString("service_code"), rs.getString("consumer_code"), rs.getString("partner_code"),
                createdAt == null ? null : createdAt.toInstant());
    }
}
