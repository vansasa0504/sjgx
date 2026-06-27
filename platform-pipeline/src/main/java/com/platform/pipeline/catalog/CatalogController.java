package com.platform.pipeline.catalog;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogService catalogService;
    private final Map<Long, Application> applications = new ConcurrentHashMap<>();
    private final AtomicLong applicationIds = new AtomicLong(1);

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
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

    @GetMapping("/{id}/preview")
    @RequirePermission("catalog:view")
    public Result<PreviewResult> preview(@PathVariable long id) {
        DataCatalogItem item = requireItem(id);
        return Result.ok(new PreviewResult(List.of(), Map.of("fields", item.fieldDefinitions()), "N/A"));
    }

    @PostMapping("/{id}/apply")
    @RequirePermission("catalog:apply")
    public Result<Application> apply(@PathVariable long id, @RequestBody ApplyRequest request) {
        requireItem(id);
        Application application = new Application(applicationIds.getAndIncrement(), id, request.reason(), request.scope(), "PENDING");
        applications.put(application.id(), application);
        return Result.ok(application);
    }

    @PostMapping("/applications/{id}/approve")
    @RequirePermission("catalog:approve")
    public Result<Application> approve(@PathVariable long id) {
        Application application = Optional.ofNullable(applications.get(id))
                .orElseThrow(() -> new BusinessException("CATALOG-404", "application not found"));
        Application approved = new Application(application.id(), application.catalogId(), application.reason(),
                application.scope(), "APPROVED");
        applications.put(id, approved);
        return Result.ok(approved);
    }

    private DataCatalogItem requireItem(long id) {
        DataCatalogItem item = catalogService.findById(id);
        if (item == null) {
            throw new BusinessException("CATALOG-404", "catalog item not found");
        }
        return item;
    }

    public record PreviewResult(List<Map<String, String>> sample, Map<String, Object> stats, String qualityReport) {
    }

    public record ApplyRequest(String reason, String scope) {
    }

    public record Application(long id, long catalogId, String reason, String scope, String status) {
    }
}
