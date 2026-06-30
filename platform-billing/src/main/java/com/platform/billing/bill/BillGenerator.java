package com.platform.billing.bill;

import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRule;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.BillingUsage;
import com.platform.common.model.ServiceInvokeLog;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class BillGenerator {
    private final BillingRuleEngine ruleEngine;
    private final BillRepository repository;
    private final BillItemRepository itemRepository;
    private final BiFunction<Instant, Instant, List<ServiceInvokeLog>> logSupplier;

    public BillGenerator(BillingRuleEngine ruleEngine, BillRepository repository) {
        this(ruleEngine, repository, new InMemoryBillItemRepository(), (from, to) -> List.of());
    }

    public BillGenerator(BillingRuleEngine ruleEngine, BillRepository repository,
                         BillItemRepository itemRepository, BiFunction<Instant, Instant, List<ServiceInvokeLog>> logSupplier) {
        this.ruleEngine = ruleEngine;
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.logSupplier = logSupplier;
    }

    public Bill generate(BillType billType, BillPeriod period, LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<String, List<ServiceInvokeLog>> grouped = logSupplier.apply(from, to).stream()
                .filter(log -> !log.createdAt().isBefore(from))
                .filter(log -> log.createdAt().isBefore(to))
                .collect(Collectors.groupingBy(log -> groupKey(billType, log)));
        List<BillItem> items = grouped.entrySet().stream()
                .map(entry -> itemFor(billType, period, start, end, entry.getKey(), entry.getValue()))
                .toList();
        BigDecimal total = items.stream().map(BillItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
        String billNo = billNo(billType, period, start, end, grouped.keySet().stream().sorted().toList());
        Bill saved = repository.save(new Bill(null, billNo, billType, period, start, end, total, BillStatus.GENERATED,
                Instant.now(), Instant.now()));
        List<BillItem> savedItems = itemRepository.saveAll(saved.billNo(), saved.id(), items);
        Bill enriched = new Bill(saved.id(), saved.billNo(), saved.billType(), saved.billPeriod(), saved.periodStart(),
                saved.periodEnd(), saved.totalAmount(), saved.status(), saved.createdAt(), saved.updatedAt(), savedItems);
        repository.save(enriched);
        return enriched;
    }

    private String groupKey(BillType billType, ServiceInvokeLog log) {
        if (billType == BillType.SETTLEMENT) {
            return nonBlank(log.partnerCode(), log.serviceCode());
        }
        return log.consumerCode();
    }

    private BillingUsage usageFor(BillType billType, String targetCode, List<ServiceInvokeLog> logs) {
        long volumeBytes = logs.stream().mapToLong(ServiceInvokeLog::responseSize).sum();
        long elapsed = logs.stream().mapToLong(ServiceInvokeLog::elapsedMillis).sum();
        TargetType targetType = billType == BillType.SETTLEMENT ? TargetType.PARTNER : TargetType.CONSUMER;
        String consumerCode = billType == BillType.SETTLEMENT ? null : targetCode;
        return new BillingUsage(targetType, stableTargetId(targetCode), null, consumerCode,
                logs.size(), volumeBytes, logs.stream().map(ServiceInvokeLog::serviceCode).distinct().count(),
                Math.max(1, elapsed / 1000), 0);
    }

    private BillItem itemFor(BillType billType, BillPeriod period, LocalDate start, LocalDate end,
                             String targetCode, List<ServiceInvokeLog> logs) {
        BillingUsage usage = usageFor(billType, targetCode, logs);
        BigDecimal amount = ruleEngine.calculate(usage, end).setScale(4, java.math.RoundingMode.HALF_UP);
        BigDecimal unitPrice = ruleEngine.matchedRules(usage, end).stream()
                .filter(rule -> rule.billingModel() != com.platform.billing.model.BillingModel.BY_PACKAGE)
                .findFirst()
                .map(BillingRule::unitPrice)
                .orElseGet(() -> logs.isEmpty() ? BigDecimal.ZERO
                        : amount.divide(BigDecimal.valueOf(logs.size()), 6, java.math.RoundingMode.HALF_UP));
        String serviceCode = logs.stream().map(ServiceInvokeLog::serviceCode).distinct().limit(2).count() == 1
                ? logs.get(0).serviceCode() : null;
        String consumerCode = billType == BillType.EXPENSE ? targetCode : null;
        String partnerCode = billType == BillType.SETTLEMENT ? targetCode : null;
        return new BillItem(null, null, null, usage.targetType().name(), targetCode,
                logs.size(), unitPrice, amount, period.name() + ":" + start + ":" + end,
                serviceCode, consumerCode, partnerCode, Instant.now());
    }

    public static long stableTargetId(String targetCode) {
        byte[] digest = digest(targetCode);
        long value = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            value = (value << 8) | (digest[i] & 0xffL);
        }
        return value & Long.MAX_VALUE;
    }

    private String billNo(BillType billType, BillPeriod period, LocalDate start, LocalDate end, List<String> groupKeys) {
        String source = billType + "|" + period + "|" + start + "|" + end + "|" + String.join(",", groupKeys);
        return billType.name() + "-" + period.name() + "-" + start + "-" + end + "-"
                + HexFormat.of().formatHex(digest(source), 0, 6).toUpperCase();
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("bill id digest failed", ex);
        }
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
