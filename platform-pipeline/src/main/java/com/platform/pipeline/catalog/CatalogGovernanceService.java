package com.platform.pipeline.catalog;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class CatalogGovernanceService {
    private final CatalogService catalogService;
    private final CatalogLineageRepository lineageRepository;
    private final CatalogQualitySummaryRepository qualitySummaryRepository;
    private final CatalogApplicationRepository applicationRepository;
    private final JdbcTemplate jdbcTemplate;

    public CatalogGovernanceService(CatalogService catalogService,
                                    CatalogLineageRepository lineageRepository,
                                    CatalogQualitySummaryRepository qualitySummaryRepository,
                                    CatalogApplicationRepository applicationRepository,
                                    JdbcTemplate jdbcTemplate) {
        this.catalogService = catalogService;
        this.lineageRepository = lineageRepository;
        this.qualitySummaryRepository = qualitySummaryRepository;
        this.applicationRepository = applicationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CatalogLineage> lineage(long catalogId) {
        return lineageRepository.findByCatalogId(catalogId);
    }

    public CatalogQualitySummary qualitySummary(long catalogId) {
        return qualitySummaryRepository.findByCatalogId(catalogId)
                .orElseGet(() -> CatalogQualitySummary.empty(catalogId));
    }

    public CatalogUsageSummary usageSummary(long catalogId) {
        long applicationCount = applicationRepository.countByCatalog(catalogId);
        long invokeCount = jdbcTemplate == null ? 0L : invokeCount(catalogId);
        return new CatalogUsageSummary(catalogId, invokeCount, applicationCount, Instant.now());
    }

    public CatalogDetail detail(long catalogId) {
        return new CatalogDetail(catalogService.findById(catalogId), lineage(catalogId),
                qualitySummary(catalogId), usageSummary(catalogId));
    }

    private long invokeCount(long catalogId) {
        List<String> serviceCodes = lineage(catalogId).stream()
                .filter(lineage -> CatalogLineage.DATA_SERVICE.equals(lineage.nodeType()))
                .filter(lineage -> CatalogLineage.DOWNSTREAM.equals(lineage.direction()))
                .map(CatalogLineage::nodeName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        if (serviceCodes.isEmpty()) {
            return 0L;
        }
        String placeholders = String.join(",", serviceCodes.stream().map(code -> "?").toList());
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_service_invoke_log WHERE service_code IN (" + placeholders + ")",
                Long.class, serviceCodes.toArray());
        return count == null ? 0L : count;
    }
}
