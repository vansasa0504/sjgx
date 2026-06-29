package com.platform.pipeline.catalog;

import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.AuditStatus;
import com.platform.common.auth.AuthPrincipal;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.Result;
import com.platform.common.security.JwtAuthFilter;
import com.platform.common.security.RequirePermission;
import com.platform.pipeline.service.DataServiceManager;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService catalogService;
    private final CatalogApplicationRepository applicationRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataServiceManager dataServiceManager;
    private final CatalogGovernanceService governanceService;

    public CatalogController(CatalogService catalogService) {
        this(catalogService, new InMemoryCatalogApplicationRepository(), null, null);
    }

    public CatalogController(CatalogService catalogService,
                             CatalogApplicationRepository applicationRepository,
                             AuditLogRepository auditLogRepository,
                             DataServiceManager dataServiceManager) {
        CatalogLineageRepository lineages = new InMemoryCatalogLineageRepository();
        CatalogQualitySummaryRepository quality = new InMemoryCatalogQualitySummaryRepository();
        this.catalogService = catalogService;
        this.applicationRepository = applicationRepository;
        this.auditLogRepository = auditLogRepository;
        this.dataServiceManager = dataServiceManager;
        this.governanceService = new CatalogGovernanceService(catalogService, lineages, quality, applicationRepository, null);
    }

    @Autowired
    public CatalogController(CatalogService catalogService,
                             CatalogApplicationRepository applicationRepository,
                             AuditLogRepository auditLogRepository,
                             DataServiceManager dataServiceManager,
                             CatalogGovernanceService governanceService) {
        this.catalogService = catalogService;
        this.applicationRepository = applicationRepository;
        this.auditLogRepository = auditLogRepository;
        this.dataServiceManager = dataServiceManager;
        this.governanceService = governanceService;
    }

    @GetMapping
    @RequirePermission("catalog:view")
    public Result<List<DataCatalogItem>> list(@RequestParam(required = false) String subject,
                                              @RequestParam(required = false) Long partnerId,
                                              @RequestParam(required = false) String dataType,
                                              @RequestParam(required = false) String scenario) {
        return Result.ok(catalogService.query(subject, partnerId, dataType, scenario));
    }

    @GetMapping("/search")
    @RequirePermission("catalog:view")
    public Result<List<DataCatalogItem>> search(@RequestParam(required = false) String keyword) {
        return Result.ok(catalogService.search(keyword));
    }

    @GetMapping("/{id}/meta")
    @RequirePermission("catalog:view")
    public Result<DataCatalogItem> meta(@PathVariable long id) {
        return Result.ok(requireItem(id));
    }

    @GetMapping("/{id}/lineage")
    @RequirePermission("catalog:view")
    public Result<List<CatalogLineage>> lineage(@PathVariable long id) {
        requireItem(id);
        return Result.ok(governanceService.lineage(id));
    }

    @GetMapping("/{id}/quality-summary")
    @RequirePermission("catalog:view")
    public Result<CatalogQualitySummary> qualitySummary(@PathVariable long id) {
        requireItem(id);
        return Result.ok(governanceService.qualitySummary(id));
    }

    @GetMapping("/{id}/usage-summary")
    @RequirePermission("catalog:view")
    public Result<CatalogUsageSummary> usageSummary(@PathVariable long id) {
        requireItem(id);
        return Result.ok(governanceService.usageSummary(id));
    }

    @GetMapping("/{id}/detail")
    @RequirePermission("catalog:view")
    public Result<CatalogDetail> detail(@PathVariable long id) {
        requireItem(id);
        return Result.ok(governanceService.detail(id));
    }

    @GetMapping("/{id}/preview")
    @RequirePermission("catalog:view")
    public Result<PreviewResult> preview(@PathVariable long id) {
        DataCatalogItem item = requireItem(id);
        AuthPrincipal principal = currentPrincipal();
        if (!canPreview(id, principal)) {
            throw new BusinessException("AUTH-403", "catalog preview requires approved application");
        }
        PreviewResult result = catalogService.preview(item);
        appendPreviewAudit(item, principal, result.sample().size());
        return Result.ok(result);
    }

    @PostMapping("/{id}/apply")
    @RequirePermission("catalog:apply")
    public Result<CatalogApplication> apply(@PathVariable long id, @RequestBody ApplyRequest request) {
        requireItem(id);
        String applicant = actorId(currentPrincipal());
        CatalogApplication application = applicationRepository.create(id, applicant, request.reason(), request.scope());
        appendApplicationAudit("CATALOG_APPLY", application, applicant, AuditStatus.SUCCESS);
        return Result.ok(application);
    }

    @PostMapping("/applications/{id}/approve")
    @RequirePermission("catalog:approve")
    public Result<CatalogApplication> approve(@PathVariable long id) {
        String approver = actorId(currentPrincipal());
        CatalogApplication pending = applicationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("CATALOG_APP-404", "application not found"));
        DataCatalogItem item = requireItem(pending.catalogId());
        CatalogApplication approved = applicationRepository.approve(id, approver);
        if (dataServiceManager != null && approved.scope() != null && !approved.scope().isBlank()) {
            dataServiceManager.grantCatalogPartner(approved.scope(), approved.applicant(), String.valueOf(item.partnerId()));
        }
        appendApplicationAudit("CATALOG_APPROVE", approved, approver, AuditStatus.SUCCESS);
        return Result.ok(approved);
    }

    @PostMapping("/applications/{id}/reject")
    @RequirePermission("catalog:approve")
    public Result<CatalogApplication> reject(@PathVariable long id) {
        String approver = actorId(currentPrincipal());
        return Result.ok(applicationRepository.reject(id, approver));
    }

    private DataCatalogItem requireItem(long id) {
        DataCatalogItem item = catalogService.findById(id);
        if (item == null) {
            throw new BusinessException("CATALOG-404", "catalog item not found");
        }
        return item;
    }

    private boolean canPreview(long catalogId, AuthPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return principal.hasPermission("catalog:approve")
                || applicationRepository.hasApproved(catalogId, principal.username());
    }

    private AuthPrincipal currentPrincipal() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object principal = attributes.getRequest().getAttribute(JwtAuthFilter.PRINCIPAL_ATTR);
        return principal instanceof AuthPrincipal authPrincipal ? authPrincipal : null;
    }

    private String actorId(AuthPrincipal principal) {
        return principal == null ? "system" : principal.username();
    }

    private void appendPreviewAudit(DataCatalogItem item, AuthPrincipal principal, int sampleSize) {
        if (auditLogRepository == null) {
            return;
        }
        HttpServletRequest request = currentRequest();
        auditLogRepository.append(new AuditEvent(null, null, "CATALOG_PREVIEW", "USER", actorId(principal),
                "CATALOG", String.valueOf(item.id()), "PREVIEW",
                "sampleSize=%d,catalogCode=%s".formatted(sampleSize, item.catalogCode()),
                request == null ? "" : request.getRemoteAddr(),
                request == null ? "" : request.getHeader("User-Agent"),
                AuditStatus.SUCCESS, Instant.now()));
    }

    private void appendApplicationAudit(String eventType, CatalogApplication application, String actorId, AuditStatus status) {
        if (auditLogRepository == null) {
            return;
        }
        HttpServletRequest request = currentRequest();
        auditLogRepository.append(new AuditEvent(null, traceId(request), eventType, "USER", actorId,
                "CATALOG_APPLICATION", String.valueOf(application.id()), application.status(),
                "catalogId=%d,scope=%s,applicant=%s".formatted(application.catalogId(), application.scope(), application.applicant()),
                request == null ? "" : request.getRemoteAddr(),
                request == null ? "" : request.getHeader("User-Agent"),
                status, Instant.now()));
    }

    private String traceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String traceId = request.getHeader("X-Trace-Id");
        return traceId == null || traceId.isBlank() ? null : traceId;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    public record PreviewResult(List<Map<String, String>> sample, Map<String, Object> stats, String qualityReport) {
    }

    public record ApplyRequest(String reason, String scope) {
    }
}
