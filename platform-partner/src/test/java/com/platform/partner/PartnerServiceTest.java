package com.platform.partner;

import com.platform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartnerServiceTest {
    @Test
    void runsFullLifecycle() {
        PartnerService service = new PartnerService("test-key");
        Partner partner = service.create("政务数据中心");

        service.apply(partner.id(), PartnerEvent.SUBMIT);
        service.apply(partner.id(), PartnerEvent.APPROVE);
        service.apply(partner.id(), PartnerEvent.ADMIT);
        service.rate(partner.id(), "A");
        service.apply(partner.id(), PartnerEvent.EXIT);

        assertEquals(PartnerStatus.EXITED, partner.status());
        assertEquals("A", partner.rating());
    }

    @Test
    void rejectsIllegalTransition() {
        PartnerService service = new PartnerService("test-key");
        Partner partner = service.create("征信联合服务");

        assertThrows(BusinessException.class, () -> service.apply(partner.id(), PartnerEvent.APPROVE));
    }

    @Test
    void encryptsInterfaceCredential() {
        PartnerService service = new PartnerService("test-key");
        Partner partner = service.create("政务数据中心");

        PartnerInterfaceConfig config = service.configureInterface(partner.id(), "HTTPS",
                "https://partner.example/api", "api-secret");

        assertNotEquals("api-secret", config.encryptedCredential());
        assertEquals("api-secret", service.revealCredential(partner.id()));
    }
}
