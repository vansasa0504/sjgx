package com.platform.billing.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryRegulatoryReportRepository implements RegulatoryReportRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, RegulatoryReportRecord> records = new ConcurrentHashMap<>();

    @Override
    public RegulatoryReportRecord save(RegulatoryReportRecord record) {
        long id = ids.getAndIncrement();
        Instant generatedAt = record.generatedAt() == null ? Instant.now() : record.generatedAt();
        RegulatoryReportRecord saved = new RegulatoryReportRecord(id, record.reportType(), record.periodFrom(),
                record.periodTo(), record.content(), record.status(), record.receiptNo(), record.receiptMessage(),
                generatedAt, record.submittedAt());
        records.put(id, saved);
        return saved;
    }

    @Override
    public RegulatoryReportRecord updateSubmission(long id, String status, String receiptNo, String receiptMessage) {
        RegulatoryReportRecord current = records.get(id);
        if (current == null) {
            return null;
        }
        RegulatoryReportRecord updated = new RegulatoryReportRecord(current.id(), current.reportType(),
                current.periodFrom(), current.periodTo(), current.content(), status, receiptNo, receiptMessage,
                current.generatedAt(), Instant.now());
        records.put(id, updated);
        return updated;
    }

    @Override
    public Optional<RegulatoryReportRecord> findById(long id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public List<RegulatoryReportRecord> findByType(String reportType) {
        return records.values().stream()
                .filter(record -> reportType == null || reportType.equals(record.reportType()))
                .sorted(Comparator.comparing(RegulatoryReportRecord::generatedAt).thenComparing(RegulatoryReportRecord::id))
                .toList();
    }
}
