package com.platform.quality.report;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryQualityReportRepository implements QualityReportRepository {
    private final AtomicLong ids = new AtomicLong(0);
    private final ConcurrentHashMap<Long, QualityReportRecord> reports = new ConcurrentHashMap<>();

    @Override
    public QualityReportRecord save(QualityReportRecord record) {
        long id = record.id() == 0 ? ids.incrementAndGet() : record.id();
        QualityReportRecord persisted = new QualityReportRecord(id, record.dimension(), record.dimensionValue(),
                record.checkCount(), record.passCount(), record.failCount(), record.failRate(),
                record.score(), record.generatedAt());
        reports.put(id, persisted);
        return persisted;
    }

    @Override
    public Optional<QualityReportRecord> findById(long id) {
        return Optional.ofNullable(reports.get(id));
    }

    @Override
    public List<QualityReportRecord> findByDimension(String dimension) {
        return reports.values().stream()
                .filter(report -> dimension == null || dimension.equals(report.dimension()))
                .sorted((left, right) -> Long.compare(left.id(), right.id()))
                .toList();
    }
}
