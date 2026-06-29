package com.platform.billing.report;

import java.util.List;
import java.util.Optional;

public interface RegulatoryReportRepository {
    RegulatoryReportRecord save(RegulatoryReportRecord record);

    RegulatoryReportRecord updateSubmission(long id, String status, String receiptNo, String receiptMessage);

    Optional<RegulatoryReportRecord> findById(long id);

    List<RegulatoryReportRecord> findByType(String reportType);
}
