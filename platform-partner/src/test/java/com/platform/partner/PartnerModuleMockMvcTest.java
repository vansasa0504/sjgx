package com.platform.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.platform.common.auth.JwtUtil;
import com.platform.common.security.PermissionCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PartnerModuleMockMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    private final ObjectMapper om = new ObjectMapper();

    private String adminToken() {
        return jwtUtil.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
    }
    private String viewerToken() {
        return jwtUtil.issue("viewer", Set.of("stats:view"), 3600);
    }

    @Test
    void partnerListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/partners").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void partnerListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/partners"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/partners").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerCreateWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/partners")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"MockMvcPartner\",\"dataType\":\"JSON\",\"industry\":\"finance\",\"complianceLevel\":\"L2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void partnerCreateNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"NoToken\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void partnerCreateInsufficientPermission() throws Exception {
        mockMvc.perform(post("/api/v1/partners")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void partnerIllegalStateTransitionReturns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/partners")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"StateTest\",\"dataType\":\"JSON\"}"))
                .andReturn();
        JsonNode node = om.readTree(result.getResponse().getContentAsString());
        long id = node.get("data").get("id").asLong();
        mockMvc.perform(post("/api/v1/partners/" + id + "/submit")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/partners/" + id + "/submit")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consumerListWithAdminToken() throws Exception {
        mockMvc.perform(get("/api/v1/consumers").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void consumerListNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/consumers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consumerListInsufficientPermission() throws Exception {
        mockMvc.perform(get("/api/v1/consumers").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void consumerRegisterWithAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/consumers")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"mock-c1\",\"name\":\"MockConsumer\",\"bizLine\":\"risk\",\"systemType\":\"core\",\"complianceLevel\":\"L2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void consumerRegisterNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/consumers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"no-token\",\"name\":\"NoToken\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consumerRegisterInsufficientPermission() throws Exception {
        mockMvc.perform(post("/api/v1/consumers")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"forbidden\",\"name\":\"Forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consumerDetailNotFoundReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/consumers/99999").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }
}