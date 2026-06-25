package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class IngestService {
    private final AtomicLong ids = new AtomicLong(1);
    private final ProtocolAdapter adapter;
    private final FormatConverter converter;
    private final RawDataRepository repository;

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository) {
        this.adapter = adapter;
        this.converter = converter;
        this.repository = repository;
    }

    public IngestTask createTask(long partnerId, URI endpoint) {
        return new IngestTask(ids.getAndIncrement(), partnerId, endpoint, adapter.protocol(), converter.format());
    }

    public List<RawDataRecord> testAndIngest(IngestTask task) {
        try {
            task.status(IngestTaskStatus.TESTING);
            String payload = adapter.fetch(task.endpoint());
            List<Map<String, String>> converted = converter.convert(payload);
            List<RawDataRecord> records = converted.stream()
                    .map(row -> new RawDataRecord(task.id(), task.partnerId(), row, Instant.now()))
                    .toList();
            repository.saveAll(records);
            task.status(IngestTaskStatus.ONLINE);
            return records;
        } catch (Exception ex) {
            throw new BusinessException("INGEST-500", "ingest failed: " + ex.getMessage());
        }
    }
}
