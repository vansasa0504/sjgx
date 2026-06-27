package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Page;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final IngestQualityGuard qualityGuard;
    private final IngestTaskStateMachine stateMachine = new IngestTaskStateMachine();

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository) {
        this(adapter, converter, repository, IngestQualityGuard.disabled());
    }

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository, IngestQualityGuard qualityGuard) {
        this.adapter = adapter;
        this.converter = converter;
        this.repository = repository;
        this.qualityGuard = qualityGuard;
    }

    public IngestTask createTask(long partnerId, URI endpoint) {
        IngestTask task = new IngestTask(ids.getAndIncrement(), partnerId, endpoint, adapter.protocol(), converter.format());
        tasks.put(task.id(), task);
        return task;
    }

    public IngestTask createTask(long partnerId, URI endpoint, String syncMode, String cronExpression,
                                 Map<String, String> fieldMapping, List<String> qualityRules) {
        IngestTask task = createTask(partnerId, endpoint);
        task.syncMode(syncMode);
        task.cronExpression(cronExpression);
        task.fieldMapping(fieldMapping);
        task.qualityRules(qualityRules);
        return task;
    }

    public List<IngestTask> list(Long partnerId, String status) {
        return tasks.values().stream()
                .filter(t -> partnerId == null || partnerId == t.partnerId())
                .filter(t -> status == null || status.isBlank() || status.equals(t.status().name()))
                .sorted(Comparator.comparingLong(IngestTask::id))
                .toList();
    }

    public IngestTask detail(long taskId) {
        return requireTask(taskId);
    }

    public IngestTask updateMapping(long taskId, Map<String, String> fieldMapping) {
        IngestTask task = requireTask(taskId);
        task.fieldMapping(fieldMapping);
        return task;
    }

    public IngestTask updateRules(long taskId, List<String> qualityRules) {
        IngestTask task = requireTask(taskId);
        task.qualityRules(qualityRules);
        return task;
    }

    public IngestTask apply(long taskId, IngestTaskEvent event) {
        IngestTask task = requireTask(taskId);
        task.status(stateMachine.transit(task.status(), event));
        return task;
    }

    public Page<RawDataRecord> records(Long taskId, int page, int size) {
        List<RawDataRecord> filtered = repository.findAll().stream()
                .filter(r -> taskId == null || taskId == r.taskId())
                .toList();
        int safeSize = size <= 0 ? 10 : size;
        int safePage = page <= 0 ? 1 : page;
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        return Page.of(new ArrayList<>(filtered.subList(from, to)), filtered.size(), safePage, safeSize);
    }

    public List<RawDataRecord> testAndIngest(IngestTask task) {
        try {
            transition(task, IngestTaskEvent.START_TEST);
            String payload = adapter.fetch(task.endpoint());
            List<Map<String, String>> converted = converter.convert(payload);
            qualityGuard.validate(converted);
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

