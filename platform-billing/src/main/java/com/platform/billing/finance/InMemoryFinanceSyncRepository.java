package com.platform.billing.finance;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryFinanceSyncRepository implements FinanceSyncRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, FinanceSyncRecord> records = new ConcurrentHashMap<>();

    @Override
    public FinanceSyncRecord save(FinanceSyncRecord record) {
        long id = ids.getAndIncrement();
        Instant syncedAt = record.syncedAt() == null ? Instant.now() : record.syncedAt();
        FinanceSyncRecord saved = new FinanceSyncRecord(id, record.billNo(), record.adapterType(),
                record.externalNo(), record.status(), record.retryCount(), record.message(), syncedAt);
        records.put(id, saved);
        return saved;
    }

    @Override
    public Optional<FinanceSyncRecord> findLastFailed(String billNo, String adapterType) {
        return records.values().stream()
                .filter(record -> billNo.equals(record.billNo()))
                .filter(record -> adapterType.equals(record.adapterType()))
                .filter(record -> "FAILED".equals(record.status()))
                .max(Comparator.comparing(FinanceSyncRecord::syncedAt).thenComparing(FinanceSyncRecord::id));
    }

    @Override
    public List<FinanceSyncRecord> findByBillNo(String billNo) {
        return records.values().stream()
                .filter(record -> billNo.equals(record.billNo()))
                .sorted(Comparator.comparing(FinanceSyncRecord::syncedAt).thenComparing(FinanceSyncRecord::id))
                .toList();
    }
}
