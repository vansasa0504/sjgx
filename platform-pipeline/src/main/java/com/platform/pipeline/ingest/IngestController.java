package com.platform.pipeline.ingest;

import com.platform.common.model.Page;
import com.platform.common.model.Result;
import com.platform.common.exception.BusinessException;
import com.platform.common.security.RequirePermission;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest/tasks")
public class IngestController {
    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    @RequirePermission("ingest:create")
    public Result<IngestTask> create(@RequestBody CreateIngestTaskRequest request) {
        return Result.ok(ingestService.createTask(request.partnerId(), URI.create(request.endpoint()),
                request.syncMode(), request.cron(), request.fieldMapping(), request.qualityRules()));
    }

    @GetMapping
    @RequirePermission("ingest:view")
    public Result<List<IngestTask>> list(@RequestParam(required = false) Long partnerId,
                                         @RequestParam(required = false) String status) {
        return Result.ok(ingestService.list(partnerId, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("ingest:view")
    public Result<IngestTask> detail(@PathVariable long id) {
        return Result.ok(ingestService.detail(id));
    }

    @PutMapping("/{id}/mapping")
    @RequirePermission("ingest:update")
    public Result<IngestTask> mapping(@PathVariable long id, @RequestBody MappingRequest request) {
        return Result.ok(ingestService.updateMapping(id, request.fieldMapping()));
    }

    @PutMapping("/{id}/rules")
    @RequirePermission("ingest:update")
    public Result<IngestTask> rules(@PathVariable long id, @RequestBody RulesRequest request) {
        return Result.ok(ingestService.updateRules(id, request.qualityRules()));
    }

    @PostMapping("/{id}/test")
    @RequirePermission("ingest:update")
    public Result<List<RawDataRecord>> run(@PathVariable long id) {
        return Result.ok(ingestService.run(id));
    }

    @PostMapping("/{id}/check")
    @RequirePermission("ingest:create")
    public Result<ConnectorCheckResult> check(@PathVariable long id) {
        ConnectorCheckResult check = ingestService.check(id);
        if (!check.ok()) {
            throw new BusinessException("INGEST-CONNECT-FAILED", check.message());
        }
        return Result.ok(check);
    }

    @PostMapping("/{id}/submit")
    @RequirePermission("ingest:approve")
    public Result<IngestTask> submit(@PathVariable long id) {
        return Result.ok(ingestService.apply(id, IngestTaskEvent.SUBMIT_APPROVAL));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("ingest:approve")
    public Result<IngestTask> approve(@PathVariable long id) {
        return Result.ok(ingestService.apply(id, IngestTaskEvent.APPROVE));
    }

    @PostMapping("/{id}/offline")
    @RequirePermission("ingest:approve")
    public Result<IngestTask> offline(@PathVariable long id) {
        return Result.ok(ingestService.apply(id, IngestTaskEvent.OFFLINE));
    }

    @GetMapping("/records")
    @RequirePermission("ingest:view")
    public Result<Page<RawDataRecord>> records(@RequestParam(required = false) Long taskId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.ok(ingestService.records(taskId, page, size));
    }

    public record CreateIngestTaskRequest(long partnerId, String endpoint, String syncMode, String cron,
                                          Map<String, String> fieldMapping, List<String> qualityRules) {
    }

    public record MappingRequest(Map<String, String> fieldMapping) {
    }

    public record RulesRequest(List<String> qualityRules) {
    }
}
