package com.platform.quality;

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
class QualityModuleMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;

    private String adminToken() {
        return jwtUtil.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
    }
    private String viewerToken() {
        return jwtUtil.issue("viewer", Set.of("stats:view"), 3600);
    }

    @Test
    void ruleListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/quality/rules").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void ruleListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/quality/rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ruleListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/quality/rules").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ruleCreateWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/quality/rules")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleCode\":\"MOCK-R1\",\"dimension\":\"COMPLETENESS\",\"field\":\"name\",\"weight\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void ruleCreateNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/quality/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleCode\":\"NT\",\"dimension\":\"COMPLETENESS\",\"field\":\"name\",\"weight\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ruleCreateInsufficientPermission() throws Exception {
        mockMvc.perform(post("/api/v1/quality/rules")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleCode\":\"FB\",\"dimension\":\"COMPLETENESS\",\"field\":\"name\",\"weight\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void triggerCheckWithInvalidRuleIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/quality/checks")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"batchNo\":\"B1\",\"ruleIds\":[99999],\"rows\":[],\"failRateThreshold\":0.1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void issueListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/quality/issues").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void issueListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/quality/issues"))
                .andExpect(status().isUnauthorized());
    }
}