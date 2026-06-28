package com.platform.partner;

import com.platform.common.audit.AuditLogger;
import com.platform.common.db.IdGenerator;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.Page;
import com.platform.common.security.Sm4Util;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;

public class PartnerService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, Partner> partners = new ConcurrentHashMap<>();
    private final Map<Long, PartnerInterfaceConfig> configs = new ConcurrentHashMap<>();
    private final PartnerStateMachine stateMachine = new PartnerStateMachine();
    private final String credentialKey;
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public PartnerService(String credentialKey) {
        this(credentialKey, null);
    }

    public PartnerService(String credentialKey, JdbcTemplate jdbcTemplate) {
        this.credentialKey = credentialKey;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate != null ? new IdGenerator(jdbcTemplate) : null;
    }

    private boolean useDb() {
        return jdbcTemplate != null;
    }

    public Partner create(String name) {
        return create(name, null, null, null);
    }

    public Partner create(String name, String dataType, String industry, String complianceLevel) {
        long id = useDb() ? idGenerator.nextId("t_partner") : ids.getAndIncrement();
        Partner partner = new Partner(id, name, dataType, industry, complianceLevel);
        if (useDb()) {
            jdbcTemplate.update("""
                    INSERT INTO t_partner
                    (id, partner_code, name, data_type, industry_type, compliance_level, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, partner.id(), "PARTNER-" + partner.id(), name, dataType, industry, complianceLevel, partner.status().name());
        }
        partners.put(partner.id(), partner);
        AuditLogger.record("partner.create:" + partner.id(), "system");
        return partner;
    }

    public Partner update(long id, String name, String dataType, String industry, String complianceLevel) {
        Partner partner = requirePartner(id);
        partner.update(dataType, industry, complianceLevel);
        if (useDb()) {
            jdbcTemplate.update("""
                    UPDATE t_partner
                    SET data_type = ?, industry_type = ?, compliance_level = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, partner.dataType(), partner.industry(), partner.complianceLevel(), id);
        }
        return partner;
    }

    public Partner apply(long id, PartnerEvent event) {
        Partner partner = requirePartner(id);
        PartnerStatus from = partner.status();
        PartnerStatus next = stateMachine.transit(partner.status(), event);
        partner.status(next);
        partner.events().add(Instant.now() + "|" + event + "|" + next);
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_partner SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    next.name(), id);
            jdbcTemplate.update("""
                    INSERT INTO t_partner_event
                    (id, partner_id, event, from_status, to_status, operator, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, idGenerator.nextId("t_partner_event"), id, event.name(), from.name(), next.name(), "system");
        }
        return partner;
    }

    public Partner rate(long id, String rating) {
        Partner partner = apply(id, PartnerEvent.RATE);
        partner.rating(rating);
        if (useDb()) {
            jdbcTemplate.update("UPDATE t_partner SET rating = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", rating, id);
        }
        return partner;
    }

    public PartnerInterfaceConfig configureInterface(long partnerId, String protocol, String endpoint, String credential) {
        requirePartner(partnerId);
        PartnerInterfaceConfig config = new PartnerInterfaceConfig(partnerId, protocol, endpoint,
                Sm4Util.encrypt(credential, credentialKey));
        configs.put(partnerId, config);
        if (useDb()) {
            jdbcTemplate.update("DELETE FROM t_partner_interface WHERE partner_id = ?", partnerId);
            jdbcTemplate.update("""
                    INSERT INTO t_partner_interface
                    (id, partner_id, protocol, endpoint, credential_cipher, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, idGenerator.nextId("t_partner_interface"), partnerId, protocol, endpoint, config.encryptedCredential());
        }
        return config;
    }

    public PartnerInterfaceConfig findInterface(long partnerId) {
        if (useDb()) {
            return jdbcTemplate.query("""
                    SELECT partner_id, protocol, endpoint, credential_cipher
                    FROM t_partner_interface WHERE partner_id = ?
                    """, (rs, rowNum) -> new PartnerInterfaceConfig(rs.getLong("partner_id"), rs.getString("protocol"),
                    rs.getString("endpoint"), rs.getString("credential_cipher")), partnerId).stream().findFirst().orElse(null);
        }
        return configs.get(partnerId);
    }

    public List<PartnerInterfaceConfig> listInterfaces(long partnerId) {
        PartnerInterfaceConfig config = findInterface(partnerId);
        return config == null ? List.of() : List.of(config);
    }

    public List<String> listEvents(long partnerId) {
        if (useDb()) {
            return jdbcTemplate.query("""
                    SELECT event, from_status, to_status, created_at
                    FROM t_partner_event WHERE partner_id = ? ORDER BY id
                    """, (rs, rowNum) -> rs.getTimestamp("created_at").toInstant() + "|" + rs.getString("event")
                    + "|" + rs.getString("to_status"), partnerId);
        }
        Partner partner = partners.get(partnerId);
        return partner == null ? List.of() : List.copyOf(partner.events());
    }

    public String revealCredential(long partnerId) {
        PartnerInterfaceConfig config = findInterface(partnerId);
        if (config == null) {
            throw new BusinessException("PARTNER-404", "interface not configured");
        }
        return Sm4Util.decrypt(config.encryptedCredential(), credentialKey);
    }

    public Page<Partner> list(String keyword, String dataType, String status, int page, int size) {
        List<Partner> source = useDb() ? listPartnersFromDb() : List.copyOf(partners.values());
        List<Partner> filtered = source.stream()
                .filter(p -> keyword == null || keyword.isBlank() || (p.name() != null && p.name().contains(keyword)))
                .filter(p -> dataType == null || dataType.isBlank() || dataType.equals(p.dataType()))
                .filter(p -> status == null || status.isBlank() || status.equals(p.status().name()))
                .sorted(Comparator.comparingLong(Partner::id))
                .toList();
        return paged(filtered, page, size);
    }

    public Optional<Partner> find(long id) {
        if (useDb()) {
            return Optional.ofNullable(loadPartner(id));
        }
        return Optional.ofNullable(partners.get(id));
    }

    private Partner requirePartner(long id) {
        Partner partner = find(id).orElse(null);
        if (partner == null) {
            throw new BusinessException("PARTNER-404", "partner not found");
        }
        return partner;
    }

    private List<Partner> listPartnersFromDb() {
        return jdbcTemplate.query("SELECT * FROM t_partner ORDER BY id", (rs, rowNum) -> mapPartner(rs.getLong("id"),
                rs.getString("name"), rs.getString("data_type"), rs.getString("industry_type"),
                rs.getString("compliance_level"), rs.getString("status"), rs.getString("rating")));
    }

    private Partner loadPartner(long id) {
        return jdbcTemplate.query("SELECT * FROM t_partner WHERE id = ?", (rs, rowNum) -> mapPartner(rs.getLong("id"),
                rs.getString("name"), rs.getString("data_type"), rs.getString("industry_type"),
                rs.getString("compliance_level"), rs.getString("status"), rs.getString("rating")), id)
                .stream().findFirst().orElse(null);
    }

    private Partner mapPartner(long id, String name, String dataType, String industry, String complianceLevel,
                               String status, String rating) {
        Partner partner = new Partner(id, name, dataType, industry, complianceLevel);
        partner.status(PartnerStatus.valueOf(status));
        partner.rating(rating);
        return partner;
    }

    private static <T> Page<T> paged(List<T> all, int page, int size) {
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return Page.of(new ArrayList<>(all.subList(from, to)), all.size(), safePage, safeSize);
    }
}
