package com.platform.pipeline.service;

import com.platform.common.model.Page;
import com.platform.common.model.Result;
import com.platform.common.model.ServiceInvokeLog;
import com.platform.common.security.RequirePermission;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
public class DataServiceController {
    private final DataServiceManager dataServiceManager;

    public DataServiceController(DataServiceManager dataServiceManager) {
        this.dataServiceManager = dataServiceManager;
    }

    @PostMapping
    @RequirePermission("service:create")
    public Result<DataServiceDefinition> register(@RequestBody RegisterServiceRequest request) {
        return Result.ok(dataServiceManager.register(request.serviceCode(), request.name(), request.routeKey()));
    }

    @GetMapping
    @RequirePermission("service:view")
    public Result<List<DataServiceDefinition>> list(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String status) {
        return Result.ok(dataServiceManager.list(keyword, status));
    }

    @GetMapping("/{serviceCode}")
    @RequirePermission("service:view")
    public Result<DataServiceDefinition> detail(@PathVariable String serviceCode) {
        return Result.ok(dataServiceManager.detail(serviceCode));
    }

    @PutMapping("/{serviceCode}")
    @RequirePermission("service:update")
    public Result<DataServiceDefinition> update(@PathVariable String serviceCode, @RequestBody UpdateServiceRequest request) {
        return Result.ok(dataServiceManager.update(serviceCode, request.name(), request.routeKey()));
    }

    @PostMapping("/{serviceCode}/test")
    @RequirePermission("service:update")
    public Result<DataServiceDefinition> test(@PathVariable String serviceCode) {
        return Result.ok(dataServiceManager.apply(serviceCode, DataServiceEvent.TEST));
    }

    @PostMapping("/{serviceCode}/define")
    @RequirePermission("service:update")
    public Result<DataServiceDefinition> define(@PathVariable String serviceCode) {
        return Result.ok(dataServiceManager.apply(serviceCode, DataServiceEvent.DEFINE));
    }

    @PostMapping("/{serviceCode}/publish")
    @RequirePermission("service:approve")
    public Result<DataServiceDefinition> publish(@PathVariable String serviceCode) {
        return Result.ok(dataServiceManager.apply(serviceCode, DataServiceEvent.PUBLISH));
    }

    @PostMapping("/{serviceCode}/offline")
    @RequirePermission("service:approve")
    public Result<DataServiceDefinition> offline(@PathVariable String serviceCode) {
        return Result.ok(dataServiceManager.apply(serviceCode, DataServiceEvent.OFFLINE));
    }

    @GetMapping("/{serviceCode}/logs")
    @RequirePermission("service:view")
    public Result<Page<ServiceInvokeLog>> logs(@PathVariable String serviceCode,
                                               @RequestParam(required = false) String consumerId,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return Result.ok(dataServiceManager.logs(serviceCode, consumerId, status, page, size));
    }

    @PostMapping("/{serviceCode}/invoke")
    public Result<String> invoke(@PathVariable String serviceCode,
                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
                                 @RequestBody InvokeRequest request) {
        return Result.ok(dataServiceManager.invoke(serviceCode, request.consumerCode(), request.apiKey(),
                request.timestamp(), request.nonce(), request.params(), request.signature(), traceId));
    }

    @GetMapping("/{serviceCode}/credentials")
    @RequirePermission("service:view")
    public Result<List<CredentialView>> listCredentials(@PathVariable String serviceCode) {
        return Result.ok(dataServiceManager.listCredentials(serviceCode).stream()
                .map(CredentialView::from)
                .toList());
    }

    @PostMapping("/{serviceCode}/credentials")
    @RequirePermission("service:update")
    public Result<CreatedCredentialResponse> createCredential(@PathVariable String serviceCode,
                                                              @RequestBody CredentialRequest request) {
        return Result.ok(CreatedCredentialResponse.from(
                dataServiceManager.createCredential(serviceCode, request.consumerCode())));
    }

    @PostMapping("/credentials/{id}/rotate")
    @RequirePermission("service:update")
    public Result<CreatedCredentialResponse> rotateCredential(@PathVariable long id) {
        return Result.ok(CreatedCredentialResponse.from(dataServiceManager.rotateCredential(id)));
    }

    @PostMapping("/credentials/{id}/disable")
    @RequirePermission("service:update")
    public Result<CredentialView> disableCredential(@PathVariable long id) {
        return Result.ok(CredentialView.from(dataServiceManager.disableCredential(id)));
    }

    public record RegisterServiceRequest(String serviceCode, String name, String routeKey) {
    }

    public record UpdateServiceRequest(String name, String routeKey) {
    }

    public record InvokeRequest(String consumerCode, String apiKey, long timestamp,
                                String nonce, String params, String signature) {
    }

    public record CredentialRequest(String consumerCode) {
    }

    public record CredentialView(long id, String apiKey, String consumerCode, String serviceCode,
                                 String status, Long rotatedFrom) {
        static CredentialView from(ApiCredentialRepository.ApiCredential credential) {
            return new CredentialView(credential.id(), credential.apiKey(), credential.consumerCode(),
                    credential.serviceCode(), credential.status(), credential.rotatedFrom());
        }
    }

    public record CreatedCredentialResponse(long id, String apiKey, String secret, String consumerCode,
                                            String serviceCode, String status) {
        static CreatedCredentialResponse from(ApiCredentialRepository.CreatedCredential credential) {
            return new CreatedCredentialResponse(credential.id(), credential.apiKey(), credential.secret(),
                    credential.consumerCode(), credential.serviceCode(), credential.status());
        }
    }
}
