package com.platform.quality;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.quality.executor.QualityCheckExecutor;
import com.platform.quality.executor.QualityCheckResult;
import com.platform.quality.issue.QualityIssue;
import com.platform.quality.issue.QualityIssueService;
import com.platform.quality.report.QualityReportRecord;
import com.platform.quality.report.QualityReportService;
import com.platform.quality.rule.QualityDimension;
import com.platform.quality.rule.QualityRuleConfig;
import com.platform.quality.rule.QualityRuleRepository;
import com.platform.quality.scoring.QualityScore;
import com.platform.quality.scoring.QualityScoringService;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final QualityRuleRepository ruleRepository;
    private final QualityCheckExecutor checkExecutor;
    private final QualityIssueService issueService;
    private final QualityScoringService scoringService;
    private final QualityReportService reportService;

    public QualityController(QualityRuleRepository ruleRepository, QualityCheckExecutor checkExecutor,
                             QualityIssueService issueService, QualityScoringService scoringService,
                             QualityReportService reportService) {
        this.ruleRepository = ruleRepository;
        this.checkExecutor = checkExecutor;
        this.issueService = issueService;
        this.scoringService = scoringService;
        this.reportService = reportService;
    }

    @PostMapping("/rules")
    @RequirePermission("quality:create")
    public Result<QualityRuleConfig> createRule(@RequestBody CreateRuleRequest request) {
        return Result.ok(ruleRepository.save(request.toConfig(null)));
    }

    @GetMapping("/rules")
    @RequirePermission("quality:view")
    public Result<List<QualityRuleConfig>> listRules(@RequestParam(required = false) QualityDimension dimension,
                                                     @RequestParam(required = false) Boolean enabled) {
        return Result.ok(ruleRepository.findAll(dimension, enabled));
    }

    @PutMapping("/rules/{id}")
    @RequirePermission("quality:update")
    public Result<QualityRuleConfig> updateRule(@PathVariable long id, @RequestBody CreateRuleRequest request) {
        ruleRepository.findById(id).orElseThrow(() -> new BusinessException("QUALITY-404", "rule not found"));
        return Result.ok(ruleRepository.save(request.toConfig(id)));
    }

    @GetMapping("/checks")
    @RequirePermission("quality:view")
    public Result<List<QualityCheckResult>> listChecks() {
        return Result.ok(checkExecutor.history());
    }

    @PostMapping("/checks")
    @RequirePermission("quality:run")
    public Result<QualityCheckResult> triggerCheck(@RequestBody TriggerCheckRequest request) {
        List<QualityRuleConfig> configs = request.ruleIds() == null ? List.of()
                : request.ruleIds().stream()
                .map(id -> ruleRepository.findById(id).orElseThrow(() -> new BusinessException("QUALITY-404", "rule not found: " + id)))
                .toList();
        return Result.ok(checkExecutor.check(request.batchNo(), request.rows(), configs, request.failRateThreshold()));
    }

    @GetMapping("/issues")
    @RequirePermission("quality:view")
    public Result<List<QualityIssue>> listIssues() {
        return Result.ok(issueService.list());
    }

    @PostMapping("/issues/{id}/assign")
    @RequirePermission("quality:update")
    public Result<QualityIssue> assign(@PathVariable long id, @RequestBody AssignRequest request) {
        return Result.ok(issueService.assign(id, request.assignee()));
    }

    @PostMapping("/issues/{id}/resolve")
    @RequirePermission("quality:update")
    public Result<QualityIssue> resolve(@PathVariable long id, @RequestBody ResolveRequest request) {
        return Result.ok(issueService.resolve(id, request.resolution()));
    }

    @PostMapping("/reports/generate")
    @RequirePermission("quality:run")
    public Result<QualityReportRecord> generateReport(@RequestBody GenerateReportRequest request) {
        Instant from = parseInstant(request.from());
        Instant to = parseInstant(request.to());
        return Result.ok(reportService.generate(request.dimension(), request.dimensionValue(), from, to));
    }

    @GetMapping("/reports")
    @RequirePermission("quality:view")
    public Result<List<QualityReportRecord>> listReports(@RequestParam(required = false) String dimension) {
        return Result.ok(reportService.list(dimension));
    }

    @GetMapping("/reports/{id}")
    @RequirePermission("quality:view")
    public Result<QualityReportRecord> reportDetail(@PathVariable long id) {
        return Result.ok(reportService.detail(id));
    }

    @GetMapping("/reports/{id}/export")
    @RequirePermission("quality:view")
    public ResponseEntity<byte[]> exportReport(@PathVariable long id) {
        QualityReportRecord report = reportService.export(id);
        String json = serializeReport(report);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=quality-report-" + id + ".json");
        return ResponseEntity.ok().headers(headers).body(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/scores")
    @RequirePermission("quality:view")
    public Result<QualityScore> score(@RequestParam(required = false, defaultValue = "ALL") String partnerId) {
        List<QualityCheckResult> history = checkExecutor.history();
        List<QualityRuleConfig> configs = ruleRepository.findAll(null, null);
        if (history.isEmpty()) {
            return Result.ok(new QualityScore(100.0, "A"));
        }
        QualityCheckResult latest = history.get(history.size() - 1);
        return Result.ok(scoringService.score(latest, configs));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException ex) {
            throw new BusinessException("QUALITY_REPORT-400", "invalid timestamp: " + value);
        }
    }

    private String serializeReport(QualityReportRecord report) {
        try {
            return MAPPER.writeValueAsString(report);
        } catch (Exception ex) {
            throw new BusinessException("QUALITY_REPORT-500", "failed to serialize report");
        }
    }

    public record CreateRuleRequest(String ruleCode, QualityDimension dimension, String field,
                                    Map<String, Object> expression, int weight) {
        QualityRuleConfig toConfig(Long id) {
            return new QualityRuleConfig(id, ruleCode, dimension, field, expression, weight);
        }
    }

    public record TriggerCheckRequest(String batchNo, List<Long> ruleIds,
                                      List<Map<String, Object>> rows, double failRateThreshold) {
    }

    public record AssignRequest(String assignee) {
    }

    public record ResolveRequest(String resolution) {
    }

    public record GenerateReportRequest(String dimension, String dimensionValue, String from, String to) {
    }
}