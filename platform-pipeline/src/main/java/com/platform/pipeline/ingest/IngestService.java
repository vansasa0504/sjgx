package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Page;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import com.platform.common.db.IdGenerator;
import com.platform.pipeline.ingest.sync.InMemoryOffsetStore;
import com.platform.pipeline.ingest.sync.OffsetStore;
import org.springframework.jdbc.core.JdbcTemplate;

public class IngestService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, IngestTask> tasks = new ConcurrentHashMap<>();
    private final ProtocolAdapter adapter;
    private final Map<String, ProtocolAdapter> adapters;
    private final Map<String, SourceConnector> connectors;
    private final FormatConverter converter;
    private final RawDataRepository repository;
    private final IngestQualityGuard qualityGuard;
    private final IngestTaskStateMachine stateMachine = new IngestTaskStateMachine();
    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final OffsetStore offsetStore;

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository) {
        this(adapter, converter, repository, IngestQualityGuard.disabled());
    }

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository, IngestQualityGuard qualityGuard) {
        this(adapter, converter, repository, qualityGuard, null);
    }

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository,
                         IngestQualityGuard qualityGuard, JdbcTemplate jdbcTemplate) {
        this(adapter, converter, repository, qualityGuard, jdbcTemplate, new InMemoryOffsetStore());
    }

    public IngestService(ProtocolAdapter adapter, FormatConverter converter, RawDataRepository repository,
                         IngestQualityGuard qualityGuard, JdbcTemplate jdbcTemplate, OffsetStore offsetStore) {
        this.adapter = adapter;
        this.adapters = new HashMap<>();
        this.adapters.put(adapter.protocol().toUpperCase(Locale.ROOT), adapter);
        this.offsetStore = offsetStore == null ? new InMemoryOffsetStore() : offsetStore;
        this.connectors = new HashMap<>();
        this.connectors.put(adapter.protocol().toUpperCase(Locale.ROOT), connector(adapter));
        this.converter = converter;
        this.repository = repository;
        this.qualityGuard = qualityGuard;
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = jdbcTemplate != null ? new IdGenerator(jdbcTemplate) : null;
    }

    public IngestService(Collection<ProtocolAdapter> adapters, ProtocolAdapter defaultAdapter,
                         FormatConverter converter, RawDataRepository repository,
                         IngestQualityGuard qualityGuard, JdbcTemplate jdbcTemplate) {
        this(adapters, defaultAdapter, converter, repository, qualityGuard, jdbcTemplate, new InMemoryOffsetStore());
    }

    public IngestService(Collection<ProtocolAdapter> adapters, ProtocolAdapter defaultAdapter,
                         FormatConverter converter, RawDataRepository repository,
                         IngestQualityGuard qualityGuard, JdbcTemplate jdbcTemplate, OffsetStore offsetStore) {
        this(defaultAdapter, converter, repository, qualityGuard, jdbcTemplate, offsetStore);
        adapters.forEach(candidate -> {
            String key = candidate.protocol().toUpperCase(Locale.ROOT);
            this.adapters.put(key, candidate);
            this.connectors.put(key, connector(candidate));
        });
    }

    private boolean useDb() {
        return jdbcTemplate != null;
    }

    public IngestTask createTask(long partnerId, URI endpoint) {
        long id = useDb() ? idGenerator.nextId("t_ingest_task") : ids.getAndIncrement();
        IngestTask task = new IngestTask(id, partnerId, endpoint, adapterFor(endpoint, null).protocol(), converter.format());
        persistTask(task);
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
        persistTask(task);
        return task;
    }

    public List<IngestTask> list(Long partnerId, String status) {
        List<IngestTask> source = useDb() ? listTasksFromDb() : List.copyOf(tasks.values());
        return source.stream()
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
        persistTask(task);
        return task;
    }

    public IngestTask updateRules(long taskId, List<String> qualityRules) {
        IngestTask task = requireTask(taskId);
        task.qualityRules(qualityRules);
        persistTask(task);
        return task;
    }

    public IngestTask apply(long taskId, IngestTaskEvent event) {
        IngestTask task = requireTask(taskId);
        if (event == IngestTaskEvent.APPROVE) {
            validateApproval(task);
        }
        task.status(stateMachine.transit(task.status(), event));
        persistTask(task);
        return task;
    }

    public List<ConnectorSpec> connectorSpecs() {
        return connectors.values().stream()
                .map(SourceConnector::spec)
                .sorted(Comparator.comparing(ConnectorSpec::protocol))
                .toList();
    }

    public ConnectorCheckResult check(long taskId) {
        IngestTask task = requireTask(taskId);
        return connectorFor(task.endpoint(), task.protocol()).check(task.endpoint());
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
            SourceConnector connector = connectorFor(task.endpoint(), task.protocol());
            long offset = offsetStore.get(AbstractSourceConnector.checkpointKey(task.id(), connector.protocol()));
            RawDataBatch batch = connector.read(task.endpoint(), offset, 1000);
            String payload = payload(batch);
            List<Map<String, String>> converted = converter.convert(payload);
            qualityGuard.validate(converted);
            List<RawDataRecord> records = converted.stream()
                    .map(row -> new RawDataRecord(task.id(), task.partnerId(), row, Instant.now()))
                    .toList();
            repository.saveAll(records);
            connector.checkpoint(task.id(), batch.nextOffset());
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
        persistTask(task);
        return task;
    }

    public IngestTask requireTask(long taskId) {
        IngestTask task = useDb() ? loadTask(taskId) : tasks.get(taskId);
        if (task == null) {
            throw new BusinessException("INGEST-404", "ingest task not found");
        }
        return task;
    }

    public List<RawDataRecord> records() {
        return repository.findAll();
    }

    private void persistTask(IngestTask task) {
        if (!useDb()) {
            return;
        }
        int affected = jdbcTemplate.update("""
                UPDATE t_ingest_task
                SET sync_mode = ?, schedule_cron = ?, mapping_config = ?, rule_config = ?, status = ?, protocol = ?, format = ?, endpoint = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, task.syncMode(), task.cronExpression(), task.fieldMapping().toString(),
                String.join(",", task.qualityRules()), task.status().name(),
                task.protocol(), task.format(), task.endpoint().toString(), task.id());
        if (affected == 0) {
            jdbcTemplate.update("""
                    INSERT INTO t_ingest_task
                    (id, partner_id, protocol, format, endpoint, sync_mode, schedule_cron, mapping_config, rule_config, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, task.id(), task.partnerId(), task.protocol(), task.format(), task.endpoint().toString(),
                    task.syncMode(), task.cronExpression(), task.fieldMapping().toString(),
                    String.join(",", task.qualityRules()), task.status().name());
        }
    }

    private List<IngestTask> listTasksFromDb() {
        return jdbcTemplate.query("SELECT * FROM t_ingest_task ORDER BY id", (rs, rowNum) -> mapTask(
                rs.getLong("id"), rs.getLong("partner_id"), rs.getString("endpoint"),
                rs.getString("protocol"), rs.getString("format"), rs.getString("sync_mode"),
                rs.getString("schedule_cron"), rs.getString("status"),
                rs.getString("mapping_config"), rs.getString("rule_config")));
    }

    private IngestTask loadTask(long taskId) {
        return jdbcTemplate.query("SELECT * FROM t_ingest_task WHERE id = ?", (rs, rowNum) -> mapTask(
                rs.getLong("id"), rs.getLong("partner_id"), rs.getString("endpoint"),
                rs.getString("protocol"), rs.getString("format"), rs.getString("sync_mode"),
                rs.getString("schedule_cron"), rs.getString("status"),
                rs.getString("mapping_config"), rs.getString("rule_config")), taskId).stream().findFirst().orElse(null);
    }

    private IngestTask mapTask(long id, long partnerId, String endpoint, String protocol, String format,
                               String syncMode, String cron, String status,
                               String mappingConfig, String ruleConfig) {
        IngestTask task = new IngestTask(id, partnerId, URI.create(endpoint), protocol, format);
        task.syncMode(syncMode);
        task.cronExpression(cron);
        if (ruleConfig != null && !ruleConfig.isBlank()) {
            task.qualityRules(java.util.Arrays.stream(ruleConfig.split(",")).filter(s -> !s.isBlank()).toList());
        }
        if (mappingConfig != null && !mappingConfig.isBlank() && !"{}".equals(mappingConfig)) {
            task.fieldMapping(Map.of("stored", mappingConfig));
        }
        task.status(IngestTaskStatus.valueOf(status));
        return task;
    }

    private ProtocolAdapter adapterFor(URI endpoint, String protocol) {
        String key = protocol == null || protocol.isBlank() ? endpoint.getScheme() : protocol;
        if (key == null || key.isBlank()) {
            return adapter;
        }
        return adapters.getOrDefault(key.toUpperCase(Locale.ROOT), adapter);
    }

    private SourceConnector connectorFor(URI endpoint, String protocol) {
        String key = protocol == null || protocol.isBlank() ? endpoint.getScheme() : protocol;
        if (key == null || key.isBlank()) {
            return connectors.get(adapter.protocol().toUpperCase(Locale.ROOT));
        }
        return connectors.getOrDefault(key.toUpperCase(Locale.ROOT), connectors.get(adapter.protocol().toUpperCase(Locale.ROOT)));
    }

    private SourceConnector connector(ProtocolAdapter adapter) {
        ConnectorSpec spec = ConnectorSpecs.forProtocol(adapter.protocol());
        return new AbstractSourceConnector(adapter, spec, offsetStore);
    }

    private void validateApproval(IngestTask task) {
        ConnectorCheckResult check = connectorFor(task.endpoint(), task.protocol()).check(task.endpoint());
        if (!check.ok()) {
            throw new BusinessException("INGEST-CONNECT-FAILED", check.message());
        }
        if (task.fieldMapping().isEmpty()) {
            throw new BusinessException("INGEST-MAPPING-MISSING", "field mapping is required before approval");
        }
        if (task.qualityRules().isEmpty()) {
            throw new BusinessException("INGEST-RULE-MISSING", "quality rules are required before approval");
        }
    }

    private String payload(RawDataBatch batch) {
        if (batch.records().isEmpty()) {
            return "[]";
        }
        if (batch.records().size() == 1) {
            return batch.records().get(0);
        }
        return "[" + String.join(",", batch.records()) + "]";
    }
}
