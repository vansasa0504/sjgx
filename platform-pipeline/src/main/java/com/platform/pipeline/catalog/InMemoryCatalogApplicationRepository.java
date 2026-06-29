package com.platform.pipeline.catalog;

import com.platform.common.exception.BusinessException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCatalogApplicationRepository implements CatalogApplicationRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final ConcurrentHashMap<Long, CatalogApplication> applications = new ConcurrentHashMap<>();

    @Override
    public CatalogApplication create(long catalogId, String applicant, String reason, String scope) {
        long id = ids.getAndIncrement();
        CatalogApplication application = new CatalogApplication(id, catalogId, applicant, reason, scope,
                CatalogApplication.PENDING, null, Instant.now(), null);
        applications.put(id, application);
        return application;
    }

    @Override
    public CatalogApplication approve(long id, String approver) {
        return transit(id, approver, CatalogApplication.APPROVED);
    }

    @Override
    public CatalogApplication reject(long id, String approver) {
        return transit(id, approver, CatalogApplication.REJECTED);
    }

    @Override
    public Optional<CatalogApplication> findById(long id) {
        return Optional.ofNullable(applications.get(id));
    }

    @Override
    public List<CatalogApplication> findByApplicant(String applicant) {
        return applications.values().stream()
                .filter(application -> application.applicant().equals(applicant))
                .toList();
    }

    @Override
    public boolean hasApproved(long catalogId, String applicant) {
        return applications.values().stream()
                .anyMatch(application -> application.catalogId() == catalogId
                        && application.applicant().equals(applicant)
                        && CatalogApplication.APPROVED.equals(application.status()));
    }

    @Override
    public long countByCatalog(long catalogId) {
        return applications.values().stream()
                .filter(application -> application.catalogId() == catalogId)
                .count();
    }

    private CatalogApplication transit(long id, String approver, String targetStatus) {
        return applications.compute(id, (key, current) -> {
            if (current == null) {
                throw new BusinessException("CATALOG_APP-404", "application not found");
            }
            if (!CatalogApplication.PENDING.equals(current.status())) {
                throw new BusinessException("CATALOG_APP-409", "application already reviewed");
            }
            return new CatalogApplication(current.id(), current.catalogId(), current.applicant(),
                    current.reason(), current.scope(), targetStatus, approver, current.createdAt(), Instant.now());
        });
    }
}
