package com.platform.quality.report;

import com.platform.common.exception.BusinessException;
import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.executor.QualityCheckResult;
import java.time.Instant;
import java.util.List;

public class QualityReportService {
    private final QualityCheckExecutor checkExecutor;
    private final QualityReportRepository repository;

    public QualityReportService(QualityCheckExecutor checkExecutor, QualityReportRepository repository) {
        this.checkExecutor = checkExecutor;
        this.repository = repository;
    }

    public QualityReportRecord generate(String dimension, String dimensionValue, Instant from, Instant to) {
        List<QualityCheckResult> matched = checkExecutor.history().stream()
                .filter(check -> matchesDimension(check, dimensionValue))
                .filter(check -> from == null || !check.checkedAt().isBefore(from))
                .filter(check -> to == null || !check.checkedAt().isAfter(to))
                .toList();
        int checkCount = matched.size();
        int failCount = (int) matched.stream().filter(check -> !check.passed()).count();
        int passCount = checkCount - failCount;
        double failRate = checkCount == 0 ? 0.0 : (double) failCount / checkCount;
        double score = checkCount == 0 ? 0.0 : 100.0 * (1.0 - failRate);
        QualityReportRecord record = new QualityReportRecord(0, dimension, dimensionValue,
                checkCount, passCount, failCount, failRate, score, Instant.now());
        return repository.save(record);
    }

    public List<QualityReportRecord> list(String dimension) {
        return repository.findByDimension(dimension);
    }

    public QualityReportRecord detail(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("QUALITY_REPORT-404", "report not found"));
    }

    public QualityReportRecord export(long id) {
        return detail(id);
    }

    private boolean matchesDimension(QualityCheckResult check, String dimensionValue) {
        if (dimensionValue == null || dimensionValue.isBlank()) {
            return true;
        }
        String batchNo = check.batchNo();
        if (batchNo == null) {
            return false;
        }
        return batchNo.equals(dimensionValue);
    }
}