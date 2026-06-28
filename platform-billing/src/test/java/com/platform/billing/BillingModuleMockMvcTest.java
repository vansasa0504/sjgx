package com.platform.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillItem;
import com.platform.billing.bill.BillItemRepository;
import com.platform.billing.bill.BillRepository;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.common.auth.JwtUtil;
import com.platform.common.security.PermissionCodes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class BillingModuleMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired BillRepository billRepository;
    @Autowired BillItemRepository billItemRepository;

    private String adminToken() {
        return jwtUtil.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
    }
    private String viewerToken() {
        return jwtUtil.issue("viewer", Set.of("stats:view"), 3600);
    }

    private static final String RULE_JSON =
            "{\"ruleCode\":\"MOCK-B1\",\"ruleName\":\"MockBill\",\"billingModel\":\"BY_COUNT\",\"targetType\":\"SERVICE\",\"targetId\":1,\"unitPrice\":1,\"currency\":\"CNY\",\"effectiveFrom\":\"2026-06-01\",\"effectiveTo\":\"2026-12-31\",\"packageAllowance\":0}";
    private static final String BILL_JSON =
            "{\"billType\":\"SETTLEMENT\",\"period\":\"MONTHLY\",\"start\":\"2026-06-01\",\"end\":\"2026-06-30\"}";
    private static final String BILL_JSON_WITH_TAMPERED_LOGS =
            "{\"billType\":\"EXPENSE\",\"period\":\"MONTHLY\",\"start\":\"2026-06-01\",\"end\":\"2026-06-30\",\"logs\":[{\"serviceCode\":\"svc\",\"consumerCode\":\"fake\",\"status\":200,\"elapsedMillis\":1,\"createdAt\":\"2026-06-02T00:00:00Z\"}]}";

    @Test
    void billingRuleListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/billing/rules").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void billingRuleListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/billing/rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void billingRuleListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/billing/rules").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void billingRuleCreateWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/billing/rules")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(RULE_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void billingRuleCreateNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/billing/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(RULE_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void billingRuleCreateInsufficientPermission() throws Exception {
        mockMvc.perform(post("/api/v1/billing/rules")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(RULE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void billGenerateWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/billing/bills/generate")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BILL_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.billNo").isString())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void billGenerateIgnoresTamperedRequestLogs() throws Exception {
        mockMvc.perform(post("/api/v1/billing/bills/generate")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BILL_JSON_WITH_TAMPERED_LOGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(0.0000))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void billGenerateNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/billing/bills/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BILL_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void billGenerateInsufficientPermission() throws Exception {
        mockMvc.perform(post("/api/v1/billing/bills/generate")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BILL_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void statsDashboardWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/stats/dashboard").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void billingStatsWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/billing/stats").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").exists())
                .andExpect(jsonPath("$.data.invokeCount").exists());
    }

    @Test
    void billingStatsFiltersByConsumerAndPartner() throws Exception {
        billItemRepository.saveAll("BILL-STATS-C", 1L, java.util.List.of(new BillItem(null, null, null,
                "CONSUMER", "1001", 2, new BigDecimal("3.000000"), new BigDecimal("6.0000"),
                "DAILY:2026-06-01:2026-06-01", "svc-c", "1001", null, Instant.now())));
        billItemRepository.saveAll("BILL-STATS-P", 2L, java.util.List.of(new BillItem(null, null, null,
                "PARTNER", "2002", 3, new BigDecimal("4.000000"), new BigDecimal("12.0000"),
                "DAILY:2026-06-01:2026-06-01", "svc-p", null, "2002", Instant.now())));

        mockMvc.perform(get("/api/v1/billing/stats")
                .param("from", "2026-06-01")
                .param("to", "2026-06-30")
                .param("consumerId", "1001")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(6.0000))
                .andExpect(jsonPath("$.data.invokeCount").value(2));

        mockMvc.perform(get("/api/v1/billing/stats")
                .param("partnerId", "2002")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(12.0000))
                .andExpect(jsonPath("$.data.invokeCount").value(3));
    }

    @Test
    void billingStatsNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/billing/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void billingStatsInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/billing/stats").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void billInvalidTransitionReturnsConflict() throws Exception {
        billRepository.save(new Bill(null, "BILL-CONFLICT", BillType.EXPENSE, BillPeriod.DAILY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), BigDecimal.ZERO,
                BillStatus.CONFIRMED, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/billing/bills/BILL-CONFLICT/dispute")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BILL_STATE_INVALID"));
    }

    @Test
    void statsDashboardNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/stats/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statsDashboardInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/stats/dashboard").header("Authorization", "Bearer " + jwtUtil.issue("v", Set.of("partner:view"), 3600)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statsAuditWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/stats/audit").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void statsAuditNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/stats/audit"))
                .andExpect(status().isUnauthorized());
    }
}
