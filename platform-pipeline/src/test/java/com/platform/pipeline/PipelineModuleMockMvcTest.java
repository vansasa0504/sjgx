package com.platform.pipeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.platform.common.auth.JwtUtil;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.security.PermissionCodes;
import com.platform.pipeline.catalog.CatalogApplicationRepository;
import com.platform.pipeline.catalog.CatalogService;
import com.platform.pipeline.service.DataServiceEvent;
import com.platform.pipeline.service.DataServiceManager;
import java.time.Instant;
import java.util.List;
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
    @Autowired DataServiceManager dataServiceManager;
    @Autowired CatalogService catalogService;
    @Autowired CatalogApplicationRepository catalogApplicationRepository;
    @Autowired AuditLogRepository auditLogRepository;

    private String adminToken() {
        return jwtUtil.issue("admin", Set.copyOf(PermissionCodes.ALL), 3600);
    }
    private String viewerToken() {
        return jwtUtil.issue("viewer", Set.of("stats:view"), 3600);
    }
    private String catalogViewerToken() {
        return jwtUtil.issue("catalog-viewer", Set.of("catalog:view"), 3600);
    }
    private String catalogApplicantToken() {
        return jwtUtil.issue("consumer-a", Set.of("catalog:view", "catalog:apply"), 3600);
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
    void connectorMatrixRequiresPermissionAndReturnsEightProtocols() throws Exception {
        mockMvc.perform(get("/api/v1/ingest/connectors"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/ingest/connectors").header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ingest/connectors").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(8))
                .andExpect(jsonPath("$.data[?(@.protocol=='HTTP')]").isArray())
                .andExpect(jsonPath("$.data[?(@.protocol=='DB')]").isArray());
    }

    @Test
    void connectorCheckRequiresCreatePermissionAndReportsFailureAsClientError() throws Exception {
        String response = mockMvc.perform(post("/api/v1/ingest/tasks")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partnerId\":1,\"endpoint\":\"http://127.0.0.1:1/data\",\"syncMode\":\"FULL\",\"cron\":\"\",\"fieldMapping\":{\"id\":\"id\"},\"qualityRules\":[\"required-id\"]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long taskId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.id")).longValue();

        mockMvc.perform(post("/api/v1/ingest/tasks/%d/check".formatted(taskId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/ingest/tasks/%d/check".formatted(taskId))
                .header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/ingest/tasks/%d/check".formatted(taskId))
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().is4xxClientError());
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
    void catalogApplyRequiresPermissionAndPersistsApplication() throws Exception {
        long catalogId = createCatalogItem("cat-apply").id();

        mockMvc.perform(post("/api/v1/catalog/%d/apply".formatted(catalogId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"风控准入\",\"scope\":\"svc-risk\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/catalog/%d/apply".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogViewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"风控准入\",\"scope\":\"svc-risk\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/catalog/%d/apply".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogApplicantToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"风控准入\",\"scope\":\"svc-risk\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.applicant").value("consumer-a"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void catalogApproveRequiresPermissionAndTransitionsState() throws Exception {
        long catalogId = createCatalogItem("cat-approve").id();
        long applicationId = catalogApplicationRepository.create(catalogId, "consumer-a", "reason", "svc-risk").id();

        mockMvc.perform(post("/api/v1/catalog/applications/%d/approve".formatted(applicationId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/catalog/applications/%d/approve".formatted(applicationId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/catalog/applications/%d/approve".formatted(applicationId))
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approver").value("admin"));
    }

    @Test
    void catalogApplyApprovePropagatesTraceIdToAuditEvents() throws Exception {
        long catalogId = createCatalogItem("cat-trace").id();
        String traceId = "trace-catalog-apply-approve";

        String response = mockMvc.perform(post("/api/v1/catalog/%d/apply".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogApplicantToken())
                .header("X-Trace-Id", traceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"风控准入\",\"scope\":\"svc-risk\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long applicationId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.data.id")).longValue();

        mockMvc.perform(post("/api/v1/catalog/applications/%d/approve".formatted(applicationId))
                .header("Authorization", "Bearer " + adminToken())
                .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        List<com.platform.common.audit.AuditEvent> events = auditLogRepository.findByTraceId(traceId);
        org.junit.jupiter.api.Assertions.assertEquals(2, events.size());
        org.junit.jupiter.api.Assertions.assertEquals("CATALOG_APPLY", events.get(0).eventType());
        org.junit.jupiter.api.Assertions.assertEquals("CATALOG_APPROVE", events.get(1).eventType());
    }

    @Test
    void catalogRejectRequiresPermissionAndTransitionsState() throws Exception {
        long catalogId = createCatalogItem("cat-reject").id();
        long applicationId = catalogApplicationRepository.create(catalogId, "consumer-a", "reason", "svc-risk").id();

        mockMvc.perform(post("/api/v1/catalog/applications/%d/reject".formatted(applicationId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/catalog/applications/%d/reject".formatted(applicationId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/catalog/applications/%d/reject".formatted(applicationId))
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.approver").value("admin"));

        mockMvc.perform(post("/api/v1/catalog/applications/99999999/reject")
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/catalog/applications/%d/reject".formatted(applicationId))
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    void catalogApproveMissingCatalogDoesNotPersistApproval() throws Exception {
        long applicationId = catalogApplicationRepository.create(999999L, "consumer-a", "reason", "svc-risk").id();

        mockMvc.perform(post("/api/v1/catalog/applications/%d/approve".formatted(applicationId))
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals("PENDING",
                catalogApplicationRepository.findById(applicationId).orElseThrow().status());
    }

    @Test
    void catalogPreviewRequiresApprovalMasksSensitiveFieldsAndWritesAudit() throws Exception {
        long catalogId = createCatalogItem("cat-preview").id();

        mockMvc.perform(get("/api/v1/catalog/%d/preview".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/catalog/%d/preview".formatted(catalogId))
                .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sample[0].idCard").value("***MASKED***"))
                .andExpect(jsonPath("$.data.sample[0].credential").value("***MASKED***"))
                .andExpect(jsonPath("$.data.stats.fieldCount").value(3))
                .andExpect(jsonPath("$.data.qualityReport").isString());

        org.junit.jupiter.api.Assertions.assertFalse(auditLogRepository.findByEventType(
                "CATALOG_PREVIEW", Instant.now().minusSeconds(60), Instant.now().plusSeconds(60)).isEmpty());
    }

    @Test
    void catalogPreviewAllowsApprovedApplicant() throws Exception {
        long catalogId = createCatalogItem("cat-preview-applicant").id();
        long applicationId = catalogApplicationRepository.create(catalogId, "consumer-a", "reason", "svc-risk").id();
        catalogApplicationRepository.approve(applicationId, "admin");

        mockMvc.perform(get("/api/v1/catalog/%d/preview".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogApplicantToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sample").isArray());
    }

    @Test
    void catalogGovernanceEndpointsRequirePermissionAndReturnTraceableDetail() throws Exception {
        long catalogId = createCatalogItem("cat-governance-mvc").id();
        catalogService.bindIngestTask(catalogId, 88L, "ingest-88");
        catalogService.bindService(catalogId, "svc-governance-mvc", "治理服务");
        catalogService.upsertQualitySummary(catalogId, 96.5, 2);
        catalogApplicationRepository.create(catalogId, "consumer-a", "reason", "svc-governance-mvc");

        mockMvc.perform(get("/api/v1/catalog/%d/detail".formatted(catalogId)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/catalog/%d/detail".formatted(catalogId))
                .header("Authorization", "Bearer " + viewerToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/catalog/%d/lineage".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.nodeType=='DATA_SERVICE')]").isArray());

        mockMvc.perform(get("/api/v1/catalog/%d/quality-summary".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(96.5))
                .andExpect(jsonPath("$.data.issueCount").value(2));

        mockMvc.perform(get("/api/v1/catalog/%d/usage-summary".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applicationCount").value(1));

        mockMvc.perform(get("/api/v1/catalog/%d/detail".formatted(catalogId))
                .header("Authorization", "Bearer " + catalogViewerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.catalogCode").value("cat-governance-mvc"))
                .andExpect(jsonPath("$.data.lineage.length()").value(3))
                .andExpect(jsonPath("$.data.quality.score").value(96.5))
                .andExpect(jsonPath("$.data.usage.applicationCount").value(1));
    }

    @Test
    void serviceInvokeIsWhitelistedNoToken() throws Exception {
        mockMvc.perform(post("/api/v1/services/nonexistent/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumerCode\":\"c\",\"apiKey\":\"k\",\"timestamp\":1,\"nonce\":\"n\",\"params\":\"{}\",\"signature\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void credentialCreateRequiresServiceUpdatePermission() throws Exception {
        dataServiceManager.register("mock-credential-svc", "Mock Credential Service", "mock-credential-route");

        mockMvc.perform(post("/api/v1/services/mock-credential-svc/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumerCode\":\"consumer-a\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/services/mock-credential-svc/credentials")
                .header("Authorization", "Bearer " + viewerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumerCode\":\"consumer-a\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/services/mock-credential-svc/credentials")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"consumerCode\":\"consumer-a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKey").isString())
                .andExpect(jsonPath("$.data.secret").isString());
    }

    @Test
    void invokeWorksWithoutSecretFieldWhenSignatureIsValid() throws Exception {
        dataServiceManager.register("mock-invoke-svc", "Mock Invoke Service", "mock-invoke-route");
        dataServiceManager.apply("mock-invoke-svc", DataServiceEvent.DEFINE);
        dataServiceManager.apply("mock-invoke-svc", DataServiceEvent.TEST);
        dataServiceManager.apply("mock-invoke-svc", DataServiceEvent.PUBLISH);
        var credential = dataServiceManager.createCredential("mock-invoke-svc", "consumer-a");
        long timestamp = java.time.Instant.now().getEpochSecond();
        String signature = dataServiceManager.signatureUtil().sign(credential.apiKey(), credential.secret(), timestamp, "mvc-nonce", "{}");

        mockMvc.perform(post("/api/v1/services/mock-invoke-svc/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"consumerCode":"consumer-a","apiKey":"%s","timestamp":%d,"nonce":"mvc-nonce","params":"{}","signature":"%s"}
                        """.formatted(credential.apiKey(), timestamp, signature)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("{\"status\":\"ok\"}"));
    }

    private com.platform.pipeline.catalog.DataCatalogItem createCatalogItem(String code) {
        return catalogService.add(code, "目录资产-" + code, "征信", 1L, "CREDIT", "风控",
                List.of("name", "idCard", "credential"), "JSON", "DAILY", "TEST", "L2", "内部");
    }
}
