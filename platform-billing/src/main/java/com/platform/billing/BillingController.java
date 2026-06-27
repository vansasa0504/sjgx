package com.platform.billing;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillRepository;
import com.platform.billing.bill.BillService;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillType;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRule;
import com.platform.billing.rule.BillingRuleRepository;
import com.platform.common.model.Result;
import com.platform.common.model.ServiceInvokeLog;
import com.platform.common.security.RequirePermission;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingRuleRepository ruleRepository;
    private final BillGenerator billGenerator;
    private final BillRepository billRepository;
    private final BillService billService;

    public BillingController(BillingRuleRepository ruleRepository, BillGenerator billGenerator,
                             BillRepository billRepository, BillService billService) {
        this.ruleRepository = ruleRepository;
        this.billGenerator = billGenerator;
        this.billRepository = billRepository;
        this.billService = billService;
    }

    @GetMapping("/rules")
    @RequirePermission("billing:view")
    public Result<List<BillingRule>> listRules(@RequestParam(required = false) TargetType targetType) {
        return Result.ok(ruleRepository.findAll().stream()
                .filter(r -> targetType == null || targetType.equals(r.targetType()))
                .toList());
    }

    @PostMapping("/rules")
    @RequirePermission("billing:create")
    public Result<BillingRule> createRule(@RequestBody CreateRuleRequest request) {
        return Result.ok(ruleRepository.save(request.toRule(null)));
    }

    @PutMapping("/rules/{id}")
    @RequirePermission("billing:update")
    public Result<BillingRule> updateRule(@PathVariable long id, @RequestBody CreateRuleRequest request) {
        return Result.ok(ruleRepository.save(request.toRule(id)));
    }

    @GetMapping("/bills")
    @RequirePermission("billing:view")
    public Result<List<Bill>> listBills(@RequestParam(required = false) BillType billType,
                                        @RequestParam(required = false) String status) {
        return Result.ok(billRepository.findAll().stream()
                .filter(b -> billType == null || billType.equals(b.billType()))
                .filter(b -> status == null || status.isBlank() || status.equals(b.status().name()))
                .toList());
    }

    @PostMapping("/bills/generate")
    @RequirePermission("billing:run")
    public Result<Bill> generate(@RequestBody GenerateBillRequest request) {
        List<ServiceInvokeLog> logs = request.logs() == null ? List.of() : request.logs();
        return Result.ok(billGenerator.generate(request.billType(), request.period(),
                request.start(), request.end(), logs));
    }

    @PostMapping("/bills/{billNo}/confirm")
    @RequirePermission("billing:approve")
    public Result<Bill> confirm(@PathVariable String billNo) {
        return Result.ok(billService.confirm(billNo));
    }

    @PostMapping("/bills/{billNo}/dispute")
    @RequirePermission("billing:approve")
    public Result<Bill> dispute(@PathVariable String billNo, @RequestBody DisputeRequest request) {
        return Result.ok(billService.dispute(billNo));
    }

    public record CreateRuleRequest(String ruleCode, String ruleName, com.platform.billing.model.BillingModel billingModel,
                                    TargetType targetType, Long targetId, BigDecimal unitPrice, String currency,
                                    LocalDate effectiveFrom, LocalDate effectiveTo, long packageAllowance) {
        BillingRule toRule(Long id) {
            return new BillingRule(id, ruleCode, ruleName, billingModel, targetType, targetId, unitPrice,
                    currency, effectiveFrom, effectiveTo, "ACTIVE", packageAllowance);
        }
    }

    public record GenerateBillRequest(BillType billType, BillPeriod period, LocalDate start, LocalDate end,
                                      List<ServiceInvokeLog> logs) {
    }

    public record DisputeRequest(String reason) {
    }
}
