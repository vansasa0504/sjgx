package com.platform.billing;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillGenerator;
import com.platform.billing.bill.BillItem;
import com.platform.billing.bill.BillItemRepository;
import com.platform.billing.bill.BillRepository;
import com.platform.billing.bill.BillService;
import com.platform.billing.finance.FinanceSyncRecord;
import com.platform.billing.finance.FinanceSyncService;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillType;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRule;
import com.platform.billing.rule.BillingRuleRepository;
import com.platform.common.exception.BusinessException;
import com.platform.common.model.Result;
import com.platform.common.security.RequirePermission;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingRuleRepository ruleRepository;
    private final BillGenerator billGenerator;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final BillService billService;
    private final FinanceSyncService financeSyncService;

    public BillingController(BillingRuleRepository ruleRepository, BillGenerator billGenerator,
                             BillRepository billRepository, BillItemRepository billItemRepository,
                             BillService billService, FinanceSyncService financeSyncService) {
        this.ruleRepository = ruleRepository;
        this.billGenerator = billGenerator;
        this.billRepository = billRepository;
        this.billItemRepository = billItemRepository;
        this.billService = billService;
        this.financeSyncService = financeSyncService;
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
        return Result.ok(billGenerator.generate(request.billType(), request.period(),
                request.start(), request.end()));
    }

    @GetMapping("/bills/{billNo}")
    @RequirePermission("billing:view")
    public Result<Bill> detail(@PathVariable String billNo) {
        return Result.ok(billRepository.findByBillNo(billNo).orElseThrow(
                () -> new BusinessException("BILL-404", "bill not found: " + billNo)));
    }

    @PostMapping("/bills/{billNo}/confirm")
    @RequirePermission("billing:approve")
    public ResponseEntity<Result<Bill>> confirm(@PathVariable String billNo) {
        return statusChange(() -> billService.confirm(billNo));
    }

    @PostMapping("/bills/{billNo}/dispute")
    @RequirePermission("billing:approve")
    public ResponseEntity<Result<Bill>> dispute(@PathVariable String billNo, @RequestBody DisputeRequest request) {
        return statusChange(() -> billService.dispute(billNo));
    }

    @PostMapping("/bills/{billNo}/adjust")
    @RequirePermission("billing:approve")
    public ResponseEntity<Result<Bill>> adjust(@PathVariable String billNo) {
        return statusChange(() -> billService.adjust(billNo));
    }

    @PostMapping("/bills/{billNo}/sync")
    @RequirePermission("billing:run")
    public Result<FinanceSyncRecord> syncBill(@PathVariable String billNo,
                                              @RequestParam(required = false) String adapterType) {
        return Result.ok(financeSyncService.sync(billNo, adapterType));
    }

    @PostMapping("/bills/{billNo}/sync/retry")
    @RequirePermission("billing:run")
    public Result<FinanceSyncRecord> retryBillSync(@PathVariable String billNo,
                                                   @RequestParam(required = false) String adapterType) {
        return Result.ok(financeSyncService.retry(billNo, adapterType));
    }

    @GetMapping("/bills/{billNo}/sync")
    @RequirePermission("billing:view")
    public Result<List<FinanceSyncRecord>> billSyncRecords(@PathVariable String billNo) {
        return Result.ok(financeSyncService.list(billNo));
    }

    @GetMapping("/stats")
    @RequirePermission("billing:view")
    public Result<BillingStats> stats(@RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to,
                                      @RequestParam(required = false) Long partnerId,
                                      @RequestParam(required = false) Long consumerId,
                                      @RequestParam(required = false) String partnerCode,
                                      @RequestParam(required = false) String consumerCode) {
        List<BillItem> filtered = billItemRepository.findAll().stream()
                .filter(item -> inRange(item, from, to))
                .filter(item -> matchesTarget(item, "PARTNER", partnerId, partnerCode))
                .filter(item -> matchesTarget(item, "CONSUMER", consumerId, consumerCode))
                .toList();
        BigDecimal total = filtered.stream().map(BillItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
        long invokeCount = filtered.stream().mapToLong(BillItem::quantity).sum();
        Map<String, BigDecimal> byItemType = filtered.stream().collect(Collectors.groupingBy(BillItem::itemType,
                Collectors.mapping(BillItem::amount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        return Result.ok(new BillingStats(total, invokeCount,
                filtered.stream().map(BillItem::billNo).distinct().count(), filtered.size(), byItemType));
    }

    public record CreateRuleRequest(String ruleCode, String ruleName, com.platform.billing.model.BillingModel billingModel,
                                    TargetType targetType, Long targetId, BigDecimal unitPrice, String currency,
                                    LocalDate effectiveFrom, LocalDate effectiveTo, long packageAllowance) {
        BillingRule toRule(Long id) {
            return new BillingRule(id, ruleCode, ruleName, billingModel, targetType, targetId, unitPrice,
                    currency, effectiveFrom, effectiveTo, "ACTIVE", packageAllowance);
        }
    }

    private boolean inRange(BillItem item, LocalDate from, LocalDate to) {
        String[] parts = item.period().split(":");
        LocalDate start = parts.length >= 2 ? LocalDate.parse(parts[1]) : LocalDate.MIN;
        LocalDate end = parts.length >= 3 ? LocalDate.parse(parts[2]) : LocalDate.MAX;
        return (from == null || !end.isBefore(from)) && (to == null || !start.isAfter(to));
    }

    private boolean matchesTarget(BillItem item, String itemType, Long id, String code) {
        if (id == null && (code == null || code.isBlank())) {
            return true;
        }
        if (!itemType.equals(item.itemType())) {
            return false;
        }
        String idValue = id == null ? null : String.valueOf(id);
        return (idValue == null || idValue.equals(item.refId()) || idValue.equals(targetCode(item)))
                && (code == null || code.isBlank() || code.equals(item.refId()) || code.equals(targetCode(item)));
    }

    private String targetCode(BillItem item) {
        return "PARTNER".equals(item.itemType()) ? item.partnerCode()
                : "CONSUMER".equals(item.itemType()) ? item.consumerCode()
                : item.refId();
    }

    private ResponseEntity<Result<Bill>> statusChange(java.util.function.Supplier<Bill> action) {
        try {
            return ResponseEntity.ok(Result.ok(action.get()));
        } catch (BusinessException exception) {
            if ("BILL-409".equals(exception.code())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.fail(exception.code(), exception.getMessage()));
            }
            throw exception;
        }
    }

    public record GenerateBillRequest(BillType billType, BillPeriod period, LocalDate start, LocalDate end) {
    }

    public record DisputeRequest(String reason) {
    }

    public record BillingStats(BigDecimal totalAmount, long invokeCount, long billCount, long itemCount,
                               Map<String, BigDecimal> amountByItemType) {
    }
}
