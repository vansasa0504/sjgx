package com.platform.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PartnerAbacMockMvcTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwt;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void consumerCanReadOwnResourceButNotAnotherConsumer() throws Exception {
        long own = register("owner-a");
        long other = register("owner-b");
        String token = jwt.issue("owner-a", Set.of("consumer:view"), 3600);

        mockMvc.perform(get("/api/v1/consumers/" + own).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/consumers/" + other).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-403"));
    }

    @Test
    void partnerCredentialIsMaskedWithoutSensitiveFieldAttribute() throws Exception {
        String admin = jwt.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
        String createBody = mockMvc.perform(post("/api/v1/partners").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"FieldMaskPartner\"}"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(createBody).path("data").path("id").asLong();
        mockMvc.perform(post("/api/v1/partners/" + id + "/interfaces").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"protocol\":\"HTTPS\",\"endpoint\":\"https://example.invalid\",\"credential\":\"top-secret\"}"))
                .andExpect(status().isOk());

        String viewer = jwt.issue("partner-viewer", Set.of("partner:view"), 3600);
        mockMvc.perform(get("/api/v1/partners/" + id + "/interfaces")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].encryptedCredential").value("****"));
    }

    private long register(String code) throws Exception {
        String admin = jwt.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
        String body = mockMvc.perform(post("/api/v1/consumers").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data").path("id").asLong();
    }
}
