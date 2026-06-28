package com.platform.billing.rule;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.platform.common.db.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcBillingRuleRepository implements BillingRuleRepository {
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcBillingRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = new IdGenerator(jdbcTemplate);
    }

    @Override
    public List<BillingRule> activeRules(LocalDate billingDate) {
        Date date = Date.valueOf(billingDate);
        return jdbcTemplate.query("""
                SELECT id, rule_code, rule_name, billing_model, target_type, target_id,
                       unit_price, currency, effective_from, effective_to, status, package_allowance
                FROM t_billing_rule
                WHERE status = 'ACTIVE' AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
                """, new BillingRuleMapper(), date, date);
    }

    @Override
    public BillingRule save(BillingRule rule) {
        Long id = rule.id();
        if (id == null) {
            id =
                    idGenerator.nextId("t_billing_rule");
        }
        int existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_billing_rule WHERE id = ?", Integer.class, id);
        if (existing > 0) {
            jdbcTemplate.update("""
                    UPDATE t_billing_rule SET rule_code=?, rule_name=?, billing_model=?, target_type=?,
                        target_id=?, unit_price=?, currency=?, effective_from=?, effective_to=?, status=?, package_allowance=?
                    WHERE id=?
                    """, rule.ruleCode(), rule.ruleName(), rule.billingModel().name(),
                    rule.targetType().name(), rule.targetId(), rule.unitPrice(), rule.currency(),
                    Date.valueOf(rule.effectiveFrom()),
                    rule.effectiveTo() == null ? null : Date.valueOf(rule.effectiveTo()),
                    rule.status(), rule.packageAllowance(), id);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO t_billing_rule (id, rule_code, rule_name, billing_model, target_type,
                        target_id, unit_price, currency, effective_from, effective_to, status, package_allowance)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, id, rule.ruleCode(), rule.ruleName(), rule.billingModel().name(),
                    rule.targetType().name(), rule.targetId(), rule.unitPrice(), rule.currency(),
                    Date.valueOf(rule.effectiveFrom()),
                    rule.effectiveTo() == null ? null : Date.valueOf(rule.effectiveTo()),
                    rule.status(), rule.packageAllowance());
        }
        return new BillingRule(id, rule.ruleCode(), rule.ruleName(), rule.billingModel(),
                rule.targetType(), rule.targetId(), rule.unitPrice(), rule.currency(),
                rule.effectiveFrom(), rule.effectiveTo(), rule.status(), rule.packageAllowance());
    }

    @Override
    public List<BillingRule> findAll() {
        return jdbcTemplate.query("""
                SELECT id, rule_code, rule_name, billing_model, target_type, target_id,
                       unit_price, currency, effective_from, effective_to, status, package_allowance
                FROM t_billing_rule
                """, new BillingRuleMapper());
    }

    private static class BillingRuleMapper implements RowMapper<BillingRule> {
        @Override
        public BillingRule mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long targetId = rs.getObject("target_id") == null ? null : rs.getLong("target_id");
            LocalDate effectiveTo = rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate();
            return new BillingRule(
                    rs.getLong("id"),
                    rs.getString("rule_code"),
                    rs.getString("rule_name"),
                    com.platform.billing.model.BillingModel.valueOf(rs.getString("billing_model")),
                    com.platform.billing.model.TargetType.valueOf(rs.getString("target_type")),
                    targetId,
                    rs.getBigDecimal("unit_price"),
                    rs.getString("currency"),
                    rs.getDate("effective_from").toLocalDate(),
                    effectiveTo,
                    rs.getString("status"),
                    rs.getLong("package_allowance"));
        }
    }
}
