package com.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.auth.JwtUtil;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.PermissionCodes;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
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
class AdvancedAuthEndpointSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwt;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publicAuthenticationEndpointsReachControllersWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/mfa").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"forged\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("MFA challenge invalid"));
        mockMvc.perform(post("/api/v1/auth/login/cert").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pem\":\"forged\",\"challengeId\":\"x\",\"signature\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("invalid certificate"));
        String redirect = mockMvc.perform(get("/api/v1/auth/sso/redirect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String state = mapper.readTree(redirect).path("data").path("state").asText();
        mockMvc.perform(get("/api/v1/auth/sso/callback")
                        .param("code", "admin").param("state", state))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("invalid SSO callback"));
        mockMvc.perform(get("/api/v1/auth/sso/callback")
                        .param("code", "test-valid-code").param("state", "forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("invalid SSO state"));
    }

    @Test
    void passwordLoginChallengeCannotBypassMfaAndCompletesWithTotp() throws Exception {
        String enrollmentToken = jwt.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
        String bind = mockMvc.perform(post("/api/v1/auth/mfa/bind")
                        .header("Authorization", "Bearer " + enrollmentToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secret = mapper.readTree(bind).path("data").path("secret").asText();
        long counter = Instant.now().getEpochSecond() / 30;
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + enrollmentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", Totp.generate(secret, counter - 1)))))
                .andExpect(status().isOk());

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH-MFA-REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        String challenge = mapper.readTree(login).path("data").path("token").asText();
        String[] parts = challenge.split("\\.");
        assertEquals(4, parts.length);
        assertFalse(challenge.contains(enrollmentToken));
        assertThrows(BusinessException.class, () -> jwt.parse(challenge));
        String oldAttackExtraction = new String(
                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThrows(BusinessException.class, () -> jwt.parse(oldAttackExtraction));

        String completed = mockMvc.perform(post("/api/v1/auth/login/mfa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "challengeId", challenge,
                                "code", Totp.generate(secret, counter)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String issued = mapper.readTree(completed).path("data").path("token").asText();
        assertEquals("admin", jwt.parse(issued).username());

        mockMvc.perform(post("/api/v1/auth/mfa/unbind")
                        .header("Authorization", "Bearer " + issued)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("code", Totp.generate(secret, counter + 1)))))
                .andExpect(status().isOk());
    }

    @Test
    void enrollmentAndCertificateLifecycleEndpointsStillRequireJwt() throws Exception {
        mockMvc.perform(post("/api/v1/auth/mfa/bind")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("missing token"));
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/mfa/unbind")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/cert/bind")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/cert/revoke")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/cert/rotate")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedMfaLifecycleAndCertificateErrorsReachProtectedControllers() throws Exception {
        String token = jwt.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
        String bind = mockMvc.perform(post("/api/v1/auth/mfa/bind")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secret = mapper.readTree(bind).path("data").path("secret").asText();
        long counter = Instant.now().getEpochSecond() / 30;
        String code = Totp.generate(secret, counter);
        mockMvc.perform(post("/api/v1/auth/mfa/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/mfa/unbind")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.generate(secret, counter + 1) + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/login/cert").param("fingerprint", "missing"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/cert/bind")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pem\":\"forged\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/cert/revoke")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fingerprint\":\"missing\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/cert/rotate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldFingerprint\":\"missing\",\"pem\":\"forged\"}"))
                .andExpect(status().isUnauthorized());
    }
}
