package com.platform.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.platform.common.auth.JwtUtil;
import com.platform.common.security.PermissionCodes;
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

    private String adminToken() {
        return jwtUtil.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
    }
    private String viewerToken() {
        return jwtUtil.issue("viewer", Set.of("stats:view"), 3600);
    }

    private static final String RULE_JSON =
            "{\"ruleCode\":\"MOCK-B1\",\"ruleName\":\"MockBill\",\"billingModel\":\"BY_COUNT\",\"targetType\":\"SERVICE\",\"targetId\":1,\"unitPrice\":1,\"currency\":\"CNY\",\"effectiveFrom\":\"2026-06-01\",\"effectiveTo\":\"2026-12-31\",\"packageAllowance\":0}";
    private static final String BILL_JSON =
            "{\"billType\":\"SETTLEMENT\",\"period\":\"MONTHLY\",\"start\":\"2026-06-01\",\"end\":\"2026-06-30\",\"logs\":[]}";

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
                .andExpect(jsonPath("$.data.billNo").isString());
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