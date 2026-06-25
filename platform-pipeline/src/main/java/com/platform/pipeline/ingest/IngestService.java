package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class IngestService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, IngestTask> tasks = new ConcurrentHashMap<>();
    private final ProtocolAdapter adapter;
    private final FormatConverter converter;
    private final RawDataRepository repository;
    private final IngestTaskStateMachine stateMachine = new IngestTaskStateMachine();

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository) {
        this.adapter = adapter;
        this.converter = converter;
        this.repository = repository;
    }

    public IngestTask createTask(long partnerId, URI endpoint) {
        IngestTask task = new IngestTask(ids.getAndIncrement(), partnerId, endpoint, adapter.protocol(), converter.format());
        tasks.put(task.id(), task);
        return task;
    }

    public List<RawDataRecord> testAndIngest(IngestTask task) {
        try {
            transition(task, IngestTaskEvent.START_TEST);
            String payload = adapter.fetch(task.endpoint());
            List<Map<String, String>> converted = converter.convert(payload);
            List<RawDataRecord> records = converted.stream()
                    .map(row -> new RawDataRecord(task.id(), task.partnerId(), row, Instant.now()))
                    .toList();
            repository.saveAll(records);
            transition(task, IngestTaskEvent.SUBMIT_APPROVAL);
            transition(task, IngestTaskEvent.APPROVE);
            return records;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("INGEST-500", "ingest failed: " + ex.getMessage());
        }
    }

    public List<RawDataRecord> run(long taskId) {
        return testAndIngest(requireTask(taskId));
    }

    public IngestTask transition(IngestTask task, IngestTaskEvent event) {
        task.status(stateMachine.transit(task.status(), event));
        return task;
    }

    public IngestTask requireTask(long taskId) {
        IngestTask task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException("INGEST-404", "ingest task not found");
        }
        return task;
    }

    public List<RawDataRecord> records() {
        return repository.findAll();
    }
}
