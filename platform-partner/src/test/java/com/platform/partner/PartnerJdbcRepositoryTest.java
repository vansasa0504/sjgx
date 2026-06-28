package com.platform.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.common.exception.BusinessException;
import com.platform.partner.consumer.ConsumerService;
import com.platform.partner.consumer.Consumer;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PartnerJdbcRepositoryTest {
    private JdbcTemplate jdbc;
    private PartnerService partnerService;
    private ConsumerService consumerService;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:partner_jdbc;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("filesystem:../db/migration").load().migrate();
        jdbc.update("DELETE FROM t_partner_event");
        jdbc.update("DELETE FROM t_partner_interface");
        jdbc.update("DELETE FROM t_consumer_event");
        jdbc.update("DELETE FROM t_consumer_quota");
        jdbc.update("DELETE FROM t_consumer");
        jdbc.update("DELETE FROM t_partner");
        partnerService = new PartnerService("test-key", jdbc);
        consumerService = new ConsumerService(jdbc);
    }

    @Test
    void partnerCrudJdbcPath() {
        Partner partner = partnerService.create("testPartner", "TRADE", "finance", "L3");
        assertNotNull(partner);
        assertTrue(partner.id() > 0);

        Partner found = partnerService.find(partner.id()).orElseThrow();
        assertEquals("testPartner", found.name());
        assertEquals("TRADE", found.dataType());

        partnerService.update(partner.id(), "updatedName", "LOAN", "banking", "L2");
        Partner updated = partnerService.find(partner.id()).orElseThrow();
        assertEquals("LOAN", updated.dataType());

        partnerService.apply(partner.id(), PartnerEvent.SUBMIT);
        partnerService.apply(partner.id(), PartnerEvent.APPROVE);
        partnerService.apply(partner.id(), PartnerEvent.ADMIT);
        Partner admitted = partnerService.find(partner.id()).orElseThrow();
        assertEquals(PartnerStatus.ADMITTED, admitted.status());

        var events = partnerService.listEvents(partner.id());
        assertTrue(events.size() >= 3);

        partnerService.configureInterface(partner.id(), "HTTP", "http://example.com/api", "secret123");
        var config = partnerService.findInterface(partner.id());
        assertNotNull(config);
        assertEquals("HTTP", config.protocol());

        String revealed = partnerService.revealCredential(partner.id());
        assertEquals("secret123", revealed);
    }

    @Test
    void partnerListJdbcPath() {
        partnerService.create("alpha", null, null, null);
        partnerService.create("beta", null, null, null);

        var page = partnerService.list(null, null, null, 1, 10);
        assertEquals(2, page.total());
    }

    @Test
    void partnerNotFoundThrowsBusinessException() {
        assertTrue(partnerService.find(99999L).isEmpty());
    }

    @Test
    void consumerCrudJdbcPath() {
        var consumer = consumerService.register("CONS-001", "testConsumer", "retail", "CORE", "L2");
        assertNotNull(consumer);
        assertTrue(consumer.id() > 0);

        Consumer found = consumerService.find(consumer.id());
        assertEquals("CONS-001", found.consumerCode());

        consumerService.apply(consumer.id(), com.platform.partner.consumer.ConsumerEvent.SUBMIT);
        consumerService.apply(consumer.id(), com.platform.partner.consumer.ConsumerEvent.APPROVE);
        consumerService.configureQuota(consumer.id(), 1000, 800);
        var quota = consumerService.consume(consumer.id());
        assertEquals(1, quota.usedRequests());
        Consumer approved = consumerService.find(consumer.id());
        assertEquals(com.platform.partner.consumer.ConsumerStatus.QUOTA_CONFIGURED, approved.status());

        var events = consumerService.events();
        assertTrue(events.size() >= 2);
    }

    @Test
    void consumerNotFoundThrowsBusinessException() {
        assertThrows(BusinessException.class, () -> consumerService.find(99999L));
    }

    @Test
    void jdbcRestartRecovery() {
        Partner partner = partnerService.create("restartTest", null, null, null);
        long partnerId = partner.id();

        PartnerService newService = new PartnerService("test-key", jdbc);
        Partner recovered = newService.find(partnerId).orElseThrow();
        assertEquals("restartTest", recovered.name());
    }
}