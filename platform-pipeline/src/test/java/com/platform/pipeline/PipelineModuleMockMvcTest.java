package com.platform.pipeline;

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

@SpringBootTest(classes = com.platform.pipeline.ingest.PipelineApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PipelineModuleMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;

    private String adminToken() {
        return jwtUtil.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
    }
    private String viewerToken() {
        return jwtUtil.issue("viewer", Set.of("stats:view"), 3600);
    }

    @Test
    void ingestListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/ingest/tasks").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void ingestListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/ingest/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/ingest/tasks").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ingestCreateWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/tasks")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\":1,\"endpoint\":\"http://localhost:9999/data\",\"syncMode\":\"FULL\",\"cron\":\"\",\"fieldMapping\":{},\"qualityRules\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void ingestCreateNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\":1,\"endpoint\":\"http://localhost:9999/data\",\"syncMode\":\"FULL\",\"cron\":\"\",\"fieldMapping\":{},\"qualityRules\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestCreateInsufficientPermission() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/tasks")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\":1,\"endpoint\":\"http://localhost:9999/data\",\"syncMode\":\"FULL\",\"cron\":\"\",\"fieldMapping\":{},\"qualityRules\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/services").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void serviceListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void serviceListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/services").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceRegisterWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceCode\":\"mock-svc\",\"name\":\"MockService\",\"routeKey\":\"mock-route\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void serviceRegisterNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"serviceCode\":\"nt\",\"name\":\"NT\",\"routeKey\":\"r\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/catalog").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void catalogListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void catalogListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/catalog").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceInvokeIsWhitelistedNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/services/nonexistent/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumerCode\":\"c\",\"apiKey\":\"k\",\"secret\":\"s\",\"timestamp\":1,\"nonce\":\"n\",\"params\":\"{}\",\"signature\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }
}