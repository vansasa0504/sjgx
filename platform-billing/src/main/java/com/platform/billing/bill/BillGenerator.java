package com.platform.billing.bill;

import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.billing.model.TargetType;
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
import java.util.stream.Collectors;

public class BillGenerator {
    private final BillingRuleEngine ruleEngine;
    private final BillRepository repository;

    public BillGenerator(BillingRuleEngine ruleEngine, BillRepository repository) {
        this.ruleEngine = ruleEngine;
        this.repository = repository;
    }

    public Bill generate(BillType billType, BillPeriod period, LocalDate start, LocalDate end, List<ServiceInvokeLog> logs) {
        Map<String, List<ServiceInvokeLog>> grouped = logs.stream()
                .filter(log -> !log.createdAt().isBefore(start.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .filter(log -> log.createdAt().isBefore(end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
                .collect(Collectors.groupingBy(log -> groupKey(billType, log)));
        BigDecimal total = grouped.entrySet().stream()
                .map(entry -> usageFor(billType, entry.getKey(), entry.getValue()))
                .map(usage -> ruleEngine.calculate(usage, end))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
        String billNo = billNo(billType, period, start, end, grouped.keySet().stream().sorted().toList());
        return repository.save(new Bill(null, billNo, billType, period, start, end, total, BillStatus.GENERATED, Instant.now(), Instant.now()));
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
