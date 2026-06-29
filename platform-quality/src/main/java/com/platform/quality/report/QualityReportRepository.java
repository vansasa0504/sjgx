package com.platform.quality.report;

import java.util.List;
import java.util.Optional;

public interface QualityReportRepository {
    QualityReportRecord save(QualityReportRecord record);

    Optional<QualityReportRecord> findById(long id);

    List<QualityReportRecord> findByDimension(String dimension);
}
