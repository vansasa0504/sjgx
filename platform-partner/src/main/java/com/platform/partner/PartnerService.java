package com.platform.partner;

import com.platform.common.audit.AuditLogger;
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

public class PartnerService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, Partner> partners = new ConcurrentHashMap<>();
    private final Map<Long, PartnerInterfaceConfig> configs = new ConcurrentHashMap<>();
    private final PartnerStateMachine stateMachine = new PartnerStateMachine();
    private final String credentialKey;

    public PartnerService(String credentialKey) {
        this.credentialKey = credentialKey;
    }

    public Partner create(String name) {
        return create(name, null, null, null);
    }

    public Partner create(String name, String dataType, String industry, String complianceLevel) {
        Partner partner = new Partner(ids.getAndIncrement(), name, dataType, industry, complianceLevel);
        partners.put(partner.id(), partner);
        AuditLogger.record("partner.create:" + partner.id(), "system");
        return partner;
    }

    public Partner update(long id, String name, String dataType, String industry, String complianceLevel) {
        Partner partner = requirePartner(id);
        partner.update(dataType, industry, complianceLevel);
        return partner;
    }

    public Partner apply(long id, PartnerEvent event) {
        Partner partner = requirePartner(id);
        PartnerStatus next = stateMachine.transit(partner.status(), event);
        partner.status(next);
        partner.events().add(Instant.now() + "|" + event + "|" + next);
        return partner;
    }

    public Partner rate(long id, String rating) {
        Partner partner = apply(id, PartnerEvent.RATE);
        partner.rating(rating);
        return partner;
    }

    public PartnerInterfaceConfig configureInterface(long partnerId, String protocol, String endpoint, String credential) {
        requirePartner(partnerId);
        PartnerInterfaceConfig config = new PartnerInterfaceConfig(partnerId, protocol, endpoint,
                Sm4Util.encrypt(credential, credentialKey));
        configs.put(partnerId, config);
        return config;
    }

    public PartnerInterfaceConfig findInterface(long partnerId) {
        return configs.get(partnerId);
    }

    public List<PartnerInterfaceConfig> listInterfaces(long partnerId) {
        PartnerInterfaceConfig config = configs.get(partnerId);
        return config == null ? List.of() : List.of(config);
    }

    public List<String> listEvents(long partnerId) {
        Partner partner = partners.get(partnerId);
        return partner == null ? List.of() : List.copyOf(partner.events());
    }

    public String revealCredential(long partnerId) {
        PartnerInterfaceConfig config = configs.get(partnerId);
        if (config == null) {
            throw new BusinessException("PARTNER-404", "interface not configured");
        }
        return Sm4Util.decrypt(config.encryptedCredential(), credentialKey);
    }

    public Page<Partner> list(String keyword, String dataType, String status, int page, int size) {
        List<Partner> filtered = partners.values().stream()
                .filter(p -> keyword == null || keyword.isBlank() || (p.name() != null && p.name().contains(keyword)))
                .filter(p -> dataType == null || dataType.isBlank() || dataType.equals(p.dataType()))
                .filter(p -> status == null || status.isBlank() || status.equals(p.status().name()))
                .sorted(Comparator.comparingLong(Partner::id))
                .toList();
        return paged(filtered, page, size);
    }

    public Optional<Partner> find(long id) {
        return Optional.ofNullable(partners.get(id));
    }

    private Partner requirePartner(long id) {
        Partner partner = partners.get(id);
        if (partner == null) {
            throw new BusinessException("PARTNER-404", "partner not found");
        }
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
