package com.platform.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class PartnerAbacAttackPathTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwt;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void consumerCannotRaiseOwnQuota() throws Exception {
        long id = register("quota-owner");
        String token = jwt.issue("quota-owner", Set.of("consumer:view", "consumer:update"), 3600);
        mockMvc.perform(put("/api/v1/consumers/" + id + "/quota")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxRequests\":9223372036854775807,\"warnThreshold\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
    }

    @Test
    void nonAdministratorListContainsOnlyOwnConsumer() throws Exception {
        register("list-owner");
        register("list-other");
        String token = jwt.issue("list-owner", Set.of("consumer:view"), 3600);
        mockMvc.perform(get("/api/v1/consumers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].consumerCode").value("list-owner"));
    }

    private long register(String code) throws Exception {
        String admin = jwt.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
        String body = mockMvc.perform(post("/api/v1/consumers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data").path("id").asLong();
    }
}
