package com.platform.partner;

import com.platform.common.exception.BusinessException;
import com.platform.common.model.Page;
import com.platform.common.model.Result;
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
@RequestMapping("/api/v1/partners")
public class PartnerController {
    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @PostMapping
    @RequirePermission("partner:create")
    public Result<Partner> create(@RequestBody CreatePartnerRequest request) {
        return Result.ok(partnerService.create(request.name(), request.dataType(), request.industry(), request.complianceLevel()));
    }

    @GetMapping
    @RequirePermission("partner:view")
    public Result<Page<Partner>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String dataType,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.ok(partnerService.list(keyword, dataType, status, page, size));
    }

    @GetMapping("/{id}")
    @RequirePermission("partner:view")
    public Result<Partner> detail(@PathVariable long id) {
        return Result.ok(partnerService.find(id).orElseThrow(() -> new BusinessException("PARTNER-404", "partner not found")));
    }

    @PutMapping("/{id}")
    @RequirePermission("partner:update")
    public Result<Partner> update(@PathVariable long id, @RequestBody UpdatePartnerRequest request) {
        return Result.ok(partnerService.update(id, request.name(), request.dataType(), request.industry(), request.complianceLevel()));
    }

    @PostMapping("/{id}/submit")
    @RequirePermission("partner:approve")
    public Result<Partner> submit(@PathVariable long id) {
        return Result.ok(partnerService.apply(id, PartnerEvent.SUBMIT));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("partner:approve")
    public Result<Partner> approve(@PathVariable long id) {
        return Result.ok(partnerService.apply(id, PartnerEvent.APPROVE));
    }

    @PostMapping("/{id}/admit")
    @RequirePermission("partner:approve")
    public Result<Partner> admit(@PathVariable long id) {
        return Result.ok(partnerService.apply(id, PartnerEvent.ADMIT));
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("partner:approve")
    public Result<Partner> reject(@PathVariable long id, @RequestBody RejectRequest request) {
        return Result.ok(partnerService.apply(id, PartnerEvent.REJECT));
    }

    @PutMapping("/{id}/rating")
    @RequirePermission("partner:update")
    public Result<Partner> rating(@PathVariable long id, @RequestBody RatingRequest request) {
        return Result.ok(partnerService.rate(id, request.score()));
    }

    @PostMapping("/{id}/terminate")
    @RequirePermission("partner:approve")
    public Result<Partner> terminate(@PathVariable long id) {
        return Result.ok(partnerService.apply(id, PartnerEvent.EXIT));
    }

    @PostMapping("/{id}/interfaces")
    @RequirePermission("partner:update")
    public Result<PartnerInterfaceConfig> configure(@PathVariable long id, @RequestBody InterfaceRequest request) {
        return Result.ok(partnerService.configureInterface(id, request.protocol(), request.endpoint(), request.credential()));
    }

    @GetMapping("/{id}/interfaces")
    @RequirePermission("partner:view")
    public Result<List<PartnerInterfaceConfig>> interfaces(@PathVariable long id) {
        return Result.ok(partnerService.listInterfaces(id));
    }

    @GetMapping("/{id}/events")
    @RequirePermission("partner:view")
    public Result<List<String>> events(@PathVariable long id) {
        return Result.ok(partnerService.listEvents(id));
    }

    public record CreatePartnerRequest(String name, String dataType, String industry, String complianceLevel) {
    }

    public record UpdatePartnerRequest(String name, String dataType, String industry, String complianceLevel) {
    }

    public record RejectRequest(String reason) {
    }

    public record RatingRequest(String score) {
    }

    public record InterfaceRequest(String protocol, String endpoint, String credential) {
    }
}
