package com.platform.billing.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.InMemoryBillItemRepository;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillType;
import com.platform.billing.model.BillingModel;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRule;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.InMemoryBillingRuleRepository;
import com.platform.billing.stats.FixedCacheMetricsProvider;
import com.platform.billing.stats.InMemoryStatsSnapshotRepository;
import com.platform.billing.stats.MetricName;
import com.platform.billing.stats.StatsAggregator;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.ServiceInvokeLog;
import com.platform.partner.Partner;
import com.platform.partner.PartnerService;
import com.platform.partner.consumer.Consumer;
import com.platform.partner.consumer.ConsumerService;
import com.platform.pipeline.ingest.HttpAdapter;
import com.platform.pipeline.ingest.IngestQualityGuard;
import com.platform.pipeline.ingest.IngestService;
import com.platform.pipeline.ingest.IngestTask;
import com.platform.pipeline.ingest.JsonConverter;
import com.platform.pipeline.ingest.RawDataRepository;
import com.platform.pipeline.service.DataServiceEvent;
import com.platform.pipeline.service.DataServiceManager;
import com.platform.pipeline.service.ApiCredentialRepository;
import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRuleConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class M5EndToEndIntegrationTest {
    @Test
    void fullChainFromPartnerIngestServiceConsumerBillingAndStatsWorks() throws Exception {
        PartnerService partnerService = new PartnerService("0123456789abcdef");
        Partner partner = partnerService.create("partner-a");
        URI endpoint = jsonServer("[{\"id\":\"1\",\"name\":\"delta\"}]");
        RawDataRepository rawDataRepository = new RawDataRepository();
        IngestService ingestService = new IngestService(new HttpAdapter(), new JsonConverter(), rawDataRepository,
                new IngestQualityGuard(new QualityCheckExecutor(), List.of(
                        new QualityRuleConfig("name-required", QualityDimension.COMPLETENESS, "name", Map.of(), 100)
                ), 0.0));
        IngestTask task = ingestService.createTask(partner.id(), endpoint);

        var records = ingestService.testAndIngest(task);

        assertEquals(1, records.size());
        assertEquals("delta", rawDataRepository.findAll().get(0).payload().get("name"));

        ConsumerService consumerService = new ConsumerService();
        Consumer consumer = consumerService.register("c1", "consumer-a", "risk", "core", "L2");
        consumerService.apply(consumer.id(), com.platform.partner.consumer.ConsumerEvent.SUBMIT);
        consumerService.apply(consumer.id(), com.platform.partner.consumer.ConsumerEvent.APPROVE);
        consumerService.configureQuota(consumer.id(), 2, 2);
        consumerService.consume(consumer.id());
        consumerService.consume(consumer.id());
        assertThrows(BusinessException.class, () -> consumerService.consume(consumer.id()));

        DataServiceManager dataServiceManager = new DataServiceManager();
        dataServiceManager.register("svc-risk", "Risk Service", "risk-route");
        dataServiceManager.apply("svc-risk", DataServiceEvent.DEFINE);
        dataServiceManager.apply("svc-risk", DataServiceEvent.TEST);
        dataServiceManager.apply("svc-risk", DataServiceEvent.PUBLISH);
        dataServiceManager.putRouteData("risk-route", "{\"score\":88}");
        ApiCredentialRepository.CreatedCredential credential = dataServiceManager.createCredential("svc-risk", "c1");
        long timestamp = Instant.now().getEpochSecond();
        String signature = dataServiceManager.signatureUtil().sign(credential.apiKey(), credential.secret(), timestamp, "n1", "{}");

        String response = dataServiceManager.invoke("svc-risk", "c1", credential.apiKey(), timestamp, "n1", "{}", signature);

        assertEquals("{\"score\":88}", response);
        assertEquals(1, dataServiceManager.logWriter().logs().size());
        assertTrue(dataServiceManager.logWriter().logs().get(0).responseSize() > 0);

        LocalDate today = LocalDate.now();
        BillingRuleEngine engine = BillingRuleEngine.defaultEngine(new InMemoryBillingRuleRepository(List.of(
                new BillingRule(null, "count-c1", "count-c1", BillingModel.BY_COUNT, TargetType.CONSUMER,
                        BillGenerator.stableTargetId("c1"), BigDecimal.ONE, "CNY", today.minusDays(1), today.plusDays(1), "ACTIVE", 0)
        )));
        Bill bill = new BillGenerator(engine, new InMemoryBillRepository(), new InMemoryBillItemRepository(),
                (from, to) -> dataServiceManager.logWriter().logs(from, to, 1, 1000).records()).generate(BillType.EXPENSE, BillPeriod.DAILY, today, today);
        assertEquals(new BigDecimal("1.0000"), bill.totalAmount());

        var snapshots = new StatsAggregator(new InMemoryStatsSnapshotRepository(), new FixedCacheMetricsProvider(new BigDecimal("0.95")))
                .aggregate(dataServiceManager.logWriter().logs(), Instant.now());
        assertEquals(new BigDecimal("1"), snapshots.stream().filter(s -> s.metricName() == MetricName.INVOKE_COUNT).findFirst().orElseThrow().metricValue());
    }

    @Test
    void criticalExceptionBranchesAreCovered() throws Exception {
        assertThrows(BusinessException.class, () -> new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository())
                .testAndIngest(new IngestTask(1, 1, URI.create("http://127.0.0.1:1/missing"), "HTTP", "JSON")));

        URI invalidJson = jsonServer("not-json");
        assertThrows(BusinessException.class, () -> new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository())
                .testAndIngest(new IngestTask(1, 1, invalidJson, "HTTP", "JSON")));

        URI dirty = jsonServer("[{\"id\":\"1\"}]");
        IngestService guarded = new IngestService(new HttpAdapter(), new JsonConverter(), new RawDataRepository(),
                new IngestQualityGuard(new QualityCheckExecutor(), List.of(
                        new QualityRuleConfig("name-required", QualityDimension.COMPLETENESS, "name", Map.of(), 100)
                ), 0.0));
        assertThrows(BusinessException.class, () -> guarded.testAndIngest(new IngestTask(1, 1, dirty, "HTTP", "JSON")));

        DataServiceManager manager = new DataServiceManager();
        manager.register("svc", "svc", "route");
        ApiCredentialRepository.CreatedCredential credential = manager.createCredential("svc", "c1");
        long ts = Instant.now().getEpochSecond();
        assertThrows(BusinessException.class, () -> manager.invoke("svc", "c1", credential.apiKey(), ts, "bad", "{}", "bad-signature"));
        manager.apply("svc", DataServiceEvent.DEFINE);
        manager.apply("svc", DataServiceEvent.TEST);
        manager.apply("svc", DataServiceEvent.PUBLISH);
        manager.putRouteData("route", "{}");
        for (int i = 0; i < 2; i++) {
            String nonce = "n" + i;
            manager.invoke("svc", "c1", credential.apiKey(), ts, nonce, "{}",
                    manager.signatureUtil().sign(credential.apiKey(), credential.secret(), ts, nonce, "{}"));
        }
        String nonce = "n3";
        assertThrows(BusinessException.class, () -> manager.invoke("svc", "c1", credential.apiKey(), ts, nonce, "{}",
                manager.signatureUtil().sign(credential.apiKey(), credential.secret(), ts, nonce, "{}")));
    }

    @Test
    void h2RunsMigrationsThroughV010AndMatchesIdentitySchema() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("m5_v010"));

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations("filesystem:../db/migration")
                .load();
        flyway.migrate();
        flyway.validate();

        assertEquals(1, jdbcTemplate.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'T_SERVICE_INVOKE_LOG' AND COLUMN_NAME = 'RESPONSE_SIZE'
                """).size());
        assertColumn(jdbcTemplate, "T_USER", "UPDATED_AT");
        assertColumn(jdbcTemplate, "T_ROLE", "CREATED_AT");
        assertColumn(jdbcTemplate, "T_PERMISSION", "CODE");
        assertColumn(jdbcTemplate, "T_USER_PERMISSION", "PERMISSION_CODE");
        assertColumn(jdbcTemplate, "T_ROLE_PERMISSION", "PERMISSION_CODE");
        assertColumn(jdbcTemplate, "T_USER_ROLE", "ROLE_ID");
        assertColumn(jdbcTemplate, "T_API_CREDENTIAL", "API_KEY");
        assertIndex(jdbcTemplate, "T_USER_PERMISSION", "UK_USER_PERM");
        assertIndex(jdbcTemplate, "T_USER_PERMISSION", "IDX_USER_PERM_USER");
        assertIndex(jdbcTemplate, "T_ROLE_PERMISSION", "UK_ROLE_PERM");
        assertIndex(jdbcTemplate, "T_USER_ROLE", "UK_USER_ROLE");
        assertIndex(jdbcTemplate, "T_API_CREDENTIAL", "IDX_API_CREDENTIAL_CONSUMER");
        assertVarcharLength(jdbcTemplate, "T_PERMISSION", "CODE", 128);
        assertVarcharLength(jdbcTemplate, "T_USER_PERMISSION", "PERMISSION_CODE", 128);
        assertVarcharLength(jdbcTemplate, "T_ROLE_PERMISSION", "PERMISSION_CODE", 128);

        jdbcTemplate.update("""
                INSERT INTO t_service_invoke_log
                (id, service_code, consumer_code, status_code, elapsed_millis, log_day, created_at, response_size)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                """, 1L, "svc-risk", "c1", 200, 35L, "20260626", 128L);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT service_code, consumer_code, status_code, response_size
                FROM t_service_invoke_log
                WHERE id = ?
                """, 1L);
        assertEquals("svc-risk", row.get("SERVICE_CODE"));
        assertEquals("c1", row.get("CONSUMER_CODE"));
        assertEquals(200, ((Number) row.get("STATUS_CODE")).intValue());
        assertEquals(128L, ((Number) row.get("RESPONSE_SIZE")).longValue());
    }

    @Test
    void h2UpgradesOldV009BaselineThroughV010WithoutLosingIdentityRows() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("m5_old_v009"));
        for (String migration : List.of("V001__init_schema.sql", "V002__partner.sql", "V003__ingest.sql", "V004__consumer.sql",
                "V005__data_service.sql", "V006__data_catalog.sql", "V007__quality_storage.sql", "V008__governance.sql", "V009__perf_and_compat.sql")) {
            executeSqlFile(jdbcTemplate, migration);
        }
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, password_hash, status, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, 100L, "legacy", "hash", "ACTIVE");
        jdbcTemplate.update("INSERT INTO t_role (id, code, name) VALUES (?, ?, ?)", 100L, "legacy-role", "Legacy Role");

        executeSqlFile(jdbcTemplate, "V010__user_role_apikey.sql");

        assertEquals("legacy", jdbcTemplate.queryForObject("SELECT username FROM t_user WHERE id = ?", String.class, 100L));
        assertEquals("legacy-role", jdbcTemplate.queryForObject("SELECT code FROM t_role WHERE id = ?", String.class, 100L));
        assertColumn(jdbcTemplate, "T_USER", "UPDATED_AT");
        assertColumn(jdbcTemplate, "T_ROLE", "CREATED_AT");
        assertTable(jdbcTemplate, "T_USER_PERMISSION");
        assertTable(jdbcTemplate, "T_ROLE_PERMISSION");
        assertTable(jdbcTemplate, "T_USER_ROLE");
        assertTable(jdbcTemplate, "T_API_CREDENTIAL");
    }

    private URI jsonServer(String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/data", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        return URI.create("http://localhost:" + server.getAddress().getPort() + "/data");
    }

    private JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        return dataSource;
    }

    private void executeSqlFile(JdbcTemplate jdbcTemplate, String migration) throws Exception {
        String sql = Files.readAllLines(Path.of("..", "db", "migration", migration)).stream()
                .map(String::trim)
                .filter(line -> !line.startsWith("--"))
                .reduce("", (left, right) -> left + System.lineSeparator() + right);
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private void assertTable(JdbcTemplate jdbcTemplate, String tableName) {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = ?
                """, Integer.class, tableName));
    }

    private void assertColumn(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName));
    }

    private void assertIndex(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        assertTrue(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                WHERE TABLE_NAME = ? AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName) >= 1);
    }

    private void assertVarcharLength(JdbcTemplate jdbcTemplate, String tableName, String columnName, int length) {
        assertEquals(length, jdbcTemplate.queryForObject("""
                SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName));
    }
}


