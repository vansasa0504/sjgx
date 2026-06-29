package com.platform.pipeline.catalog;

import java.util.List;
import java.util.Optional;

public interface CatalogApplicationRepository {
    CatalogApplication create(long catalogId, String applicant, String reason, String scope);

    CatalogApplication approve(long id, String approver);

    CatalogApplication reject(long id, String approver);

    Optional<CatalogApplication> findById(long id);

    List<CatalogApplication> findByApplicant(String applicant);

    boolean hasApproved(long catalogId, String applicant);

    long countByCatalog(long catalogId);
}
