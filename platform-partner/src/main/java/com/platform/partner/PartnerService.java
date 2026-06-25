package com.platform.partner;

import com.platform.common.audit.AuditLogger;
import com.platform.common.security.Sm4Util;

import java.time.Instant;
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
        Partner partner = new Partner(ids.getAndIncrement(), name);
        partners.put(partner.id(), partner);
        AuditLogger.record("partner.create:" + partner.id(), "system");
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

    public String revealCredential(long partnerId) {
        return Sm4Util.decrypt(configs.get(partnerId).encryptedCredential(), credentialKey);
    }

    public Optional<Partner> find(long id) {
        return Optional.ofNullable(partners.get(id));
    }

    private Partner requirePartner(long id) {
        Partner partner = partners.get(id);
        if (partner == null) {
            throw new IllegalArgumentException("partner not found");
        }
        return partner;
    }
}
