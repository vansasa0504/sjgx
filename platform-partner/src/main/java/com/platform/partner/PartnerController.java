package com.platform.partner;

import com.platform.common.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/partners")
public class PartnerController {
    private final PartnerService partnerService;

    public PartnerController(PartnerService partnerService) {
        this.partnerService = partnerService;
    }

    @PostMapping
    public Result<Partner> create(@RequestBody CreatePartnerRequest request) {
        return Result.ok(partnerService.create(request.name()));
    }

    @PostMapping("/{id}/events")
    public Result<Partner> apply(@PathVariable long id, @RequestBody PartnerEventRequest request) {
        return Result.ok(partnerService.apply(id, request.event()));
    }

    @PostMapping("/{id}/interfaces")
    public Result<PartnerInterfaceConfig> configure(@PathVariable long id, @RequestBody InterfaceRequest request) {
        return Result.ok(partnerService.configureInterface(id, request.protocol(), request.endpoint(), request.credential()));
    }

    @GetMapping("/{id}")
    public Result<Partner> detail(@PathVariable long id) {
        return Result.ok(partnerService.find(id).orElseThrow(() -> new IllegalArgumentException("partner not found")));
    }

    public record CreatePartnerRequest(String name) {
    }

    public record PartnerEventRequest(PartnerEvent event) {
    }

    public record InterfaceRequest(String protocol, String endpoint, String credential) {
    }
}
