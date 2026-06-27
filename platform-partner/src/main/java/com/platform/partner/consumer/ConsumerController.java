package com.platform.partner.consumer;

import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.model.Page;
import com.platform.common.model.Result;
import com.platform.common.model.ServiceInvokeLog;
import com.platform.common.security.RequirePermission;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/consumers")
public class ConsumerController {
    private final ConsumerService consumerService;
    private final AuditLogRepository auditLogRepository;

    public ConsumerController(ConsumerService consumerService, AuditLogRepository auditLogRepository) {
        this.consumerService = consumerService;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping
    @RequirePermission("consumer:create")
    public Result<Consumer> register(@RequestBody RegisterConsumerRequest request) {
        return Result.ok(consumerService.register(request.code(), request.name(), request.bizLine(),
                request.systemType(), request.complianceLevel()));
    }

    @GetMapping
    @RequirePermission("consumer:view")
    public Result<List<Consumer>> list(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String bizLine,
                                       @RequestParam(required = false) String status) {
        return Result.ok(consumerService.list(keyword, bizLine, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("consumer:view")
    public Result<Consumer> detail(@PathVariable long id) {
        return Result.ok(consumerService.find(id));
    }

    @PutMapping("/{id}/quota")
    @RequirePermission("consumer:update")
    public Result<ConsumerQuota> configureQuota(@PathVariable long id, @RequestBody QuotaRequest request) {
        return Result.ok(consumerService.configureQuota(id, request.maxRequests(), request.warnThreshold()));
    }

    @PostMapping("/{id}/events")
    @RequirePermission("consumer:approve")
    public Result<Consumer> apply(@PathVariable long id, @RequestBody ConsumerEventRequest request) {
        return Result.ok(consumerService.apply(id, request.event()));
    }

    @GetMapping("/{id}/audit")
    @RequirePermission("consumer:view")
    public Result<Page<AuditEvent>> audit(@PathVariable long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        Consumer consumer = consumerService.find(id);
        List<AuditEvent> events = auditLogRepository.findByActor("CONSUMER", consumer.consumerCode());
        return Result.ok(Page.of(events, events.size(), page, size));
    }

    @GetMapping("/{id}/logs")
    @RequirePermission("consumer:view")
    public Result<Page<ServiceInvokeLog>> logs(@PathVariable long id,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.ok(Page.of(List.of(), 0, page, size));
    }

    public record RegisterConsumerRequest(String code, String name, String bizLine, String systemType, String complianceLevel) {
    }

    public record QuotaRequest(long maxRequests, long warnThreshold) {
    }

    public record ConsumerEventRequest(ConsumerEvent event) {
    }
}
