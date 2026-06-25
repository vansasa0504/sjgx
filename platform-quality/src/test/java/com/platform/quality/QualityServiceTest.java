package com.platform.quality;

import com.platform.common.exception.BusinessException;
import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.executor.QualityCheckResult;
import com.platform.quality.issue.QualityIssue;
import com.platform.quality.issue.QualityIssueEvent;
import com.platform.quality.issue.QualityIssueService;
import com.platform.quality.issue.QualityIssueStatus;
import com.platform.quality.issue.QualityIssueType;
import com.platform.quality.issue.QualitySeverity;
import com.platform.quality.report.QualityReport;
import com.platform.quality.rule.AccuracyRule;
import com.platform.quality.rule.CompletenessRule;
import com.platform.quality.rule.ConsistencyRule;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRule;
import com.platform.quality.rule.QualityRuleConfig;
import com.platform.quality.rule.TimelinessRule;
import com.platform.quality.rule.UniquenessRule;
import com.platform.quality.rule.ValidityRule;
import com.platform.quality.scoring.InMemoryQualityWeightRepository;
import com.platform.quality.scoring.QualityScore;
import com.platform.quality.scoring.QualityScoringService;
import com.platform.quality.scoring.QualityWeightConfig;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualityServiceTest {
    private final Clock fixed = Clock.fixed(Instant.parse("2026-06-25T00:00:00Z"), ZoneId.of("UTC"));

    @Test
    void migrationDefinesPlanAlignedQualityAndStorageTables() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:quality-ddl;MODE=MySQL;DB_CLOSE_DELAY=-1");
        String ddl = Files.readString(findMigration());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : ddl.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
            assertColumn(statement, "T_QUALITY_RULE", "RULE_NAME");
            assertColumn(statement, "T_QUALITY_CHECK_RESULT", "PASS_COUNT");
            assertColumn(statement, "T_QUALITY_ISSUE", "ISSUE_TYPE");
            assertColumn(statement, "T_QUALITY_WEIGHT", "DIMENSION");
            assertColumn(statement, "T_STORAGE_POLICY", "COOL_TARGET");
            assertColumn(statement, "T_MARKETPLACE_DATA", "FIELDS_JSON");
            assertColumn(statement, "T_LIFECYCLE_RECORD", "ACTION");
        }
    }

    @Test
    void sixDimensionRulesHavePassingAndFailingCases() {
        assertRule(new CompletenessRule(), config("required-id", QualityDimension.COMPLETENESS, "id", Map.of()),
                List.of(Map.of("id", "1")), List.of(Map.of("id", "")));
        assertRule(new AccuracyRule(), config("score-range", QualityDimension.ACCURACY, "score", Map.of("min", 0, "max", 100)),
                List.of(Map.of("score", "90")), List.of(Map.of("score", "120")));
        assertRule(new ConsistencyRule(), config("same", QualityDimension.CONSISTENCY, "left", Map.of("equalsField", "right")),
                List.of(Map.of("left", "A", "right", "A")), List.of(Map.of("left", "A", "right", "B")));
        assertRule(new TimelinessRule(fixed), config("fresh", QualityDimension.TIMELINESS, "updatedAt", Map.of("maxAgeMinutes", 60)),
                List.of(Map.of("updatedAt", "2026-06-24T23:55:00Z")), List.of(Map.of("updatedAt", "2026-06-24T20:00:00Z")));
        assertRule(new ValidityRule(), config("phone", QualityDimension.VALIDITY, "phone", Map.of("regex", "1\\d{10}")),
                List.of(Map.of("phone", "13800138000")), List.of(Map.of("phone", "bad")));
        assertRule(new UniquenessRule(), config("unique-id", QualityDimension.UNIQUENESS, "id", Map.of()),
                List.of(Map.of("id", "1"), Map.of("id", "2")), List.of(Map.of("id", "1"), Map.of("id", "1")));
    }

    @Test
    void executorUsesRowLevelFailRateForIngestThresholds() {
        QualityCheckExecutor executor = new QualityCheckExecutor(List.of(new CompletenessRule(), new AccuracyRule()));
        List<Map<String, Object>> rows = List.of(Map.of("id", "", "score", "120"), Map.of("id", "2", "score", "80"));
        List<QualityRuleConfig> configs = List.of(
                config("required-id", QualityDimension.COMPLETENESS, "id", Map.of()),
                config("score-range", QualityDimension.ACCURACY, "score", Map.of("min", 0, "max", 100)));

        QualityCheckResult result = executor.check("INGEST", rows, configs, 0.4);

        assertFalse(result.passed());
        assertEquals(1, result.failCount());
        assertEquals(0.5, result.failRate());
        assertEquals(2, result.violations().size());
    }

    @Test
    void issueWorkflowReportAndScoringWorkTogether() {
        QualityIssueService issueService = new QualityIssueService();
        QualityIssue issue = issueService.open(1L, 2L, QualityIssueType.MISSING, QualitySeverity.ERROR, "dirty batch");

        issueService.assign(issue.id(), "alice");
        issueService.apply(issue.id(), QualityIssueEvent.START_FIX);
        issueService.apply(issue.id(), QualityIssueEvent.SUBMIT_VERIFY);
        issueService.resolve(issue.id(), "fixed by partner");
        issueService.apply(issue.id(), QualityIssueEvent.CLOSE);

        assertEquals(QualityIssueStatus.CLOSED, issue.status());
        assertEquals("fixed by partner", issue.resolution());
        assertThrows(BusinessException.class, () -> issueService.apply(issue.id(), QualityIssueEvent.CLOSE));

        QualityCheckExecutor executor = new QualityCheckExecutor(List.of(new CompletenessRule()));
        QualityRuleConfig config = config("required-id", QualityDimension.COMPLETENESS, "id", Map.of());
        QualityCheckResult result = executor.check("INGEST", List.of(Map.of("id", "1"), Map.of("id", "")), List.of(config), 1.0);
        QualityReport report = QualityReport.from("INGEST", executor.history());
        QualityScore score = new QualityScoringService().score(result, List.of(config));

        assertEquals(1, report.checkCount());
        assertEquals("D", score.grade());
        assertEquals(50.0, score.score());
    }

    @Test
    void externalWeightConfigOverridesRuleWeightsAndSameDimensionRulesDoNotAmplifyPenalty() {
        QualityRuleConfig requiredId = config("required-id", QualityDimension.COMPLETENESS, "id", Map.of());
        QualityRuleConfig requiredName = config("required-name", QualityDimension.COMPLETENESS, "name", Map.of());
        QualityCheckExecutor executor = new QualityCheckExecutor(List.of(new CompletenessRule()));
        QualityCheckResult result = executor.check("INGEST",
                List.of(Map.of("id", "", "name", "ok"), Map.of("id", "2", "name", "ok")),
                List.of(requiredId, requiredName), 1.0);

        QualityScoringService scoring = new QualityScoringService(new InMemoryQualityWeightRepository(
                List.of(new QualityWeightConfig(QualityDimension.COMPLETENESS, 100, true))));
        QualityScore score = scoring.score(result, List.of(requiredId, requiredName));

        assertEquals(75.0, score.score());
        assertEquals("C", score.grade());
    }

    private void assertRule(QualityRule rule, QualityRuleConfig config, List<Map<String, Object>> passing, List<Map<String, Object>> failing) {
        assertTrue(rule.evaluate(passing, config).isEmpty());
        assertFalse(rule.evaluate(failing, config).isEmpty());
    }

    private QualityRuleConfig config(String code, QualityDimension dimension, String field, Map<String, Object> expression) {
        return new QualityRuleConfig(code, dimension, field, expression, 100);
    }

    private Path findMigration() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4; i++) {
            Path candidate = current.resolve("db/migration/V007__quality_storage.sql");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("V007 migration not found");
    }

    private void assertColumn(Statement statement, String table, String column) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT " + column + " FROM " + table + " WHERE 1=0")) {
            assertEquals(column, rs.getMetaData().getColumnName(1));
        }
    }
}

