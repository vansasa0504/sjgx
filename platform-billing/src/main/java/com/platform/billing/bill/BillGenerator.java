package com.platform.billing.bill;

import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.billing.model.TargetType;
import com.platform.billing.rule.BillingRuleEngine;
import com.platform.billing.rule.BillingUsage;
import com.platform.pipeline.service.ServiceInvokeLog;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
        Map<String, List<ServiceInvokeLog>> byConsumer = logs.stream()
                .filter(log -> !log.createdAt().isBefore(start.atStartOfDay().toInstant(ZoneOffset.UTC)))
                .filter(log -> log.createdAt().isBefore(end.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)))
                .collect(Collectors.groupingBy(ServiceInvokeLog::consumerCode));
        BigDecimal total = byConsumer.entrySet().stream()
                .map(entry -> usageFor(entry.getKey(), entry.getValue()))
                .map(usage -> ruleEngine.calculate(usage, end))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, java.math.RoundingMode.HALF_UP);
        String billNo = period.name() + "-" + start + "-" + end + "-" + Math.abs(logs.hashCode());
        return repository.save(new Bill(null, billNo, billType, period, start, end, total, BillStatus.GENERATED, Instant.now(), Instant.now()));
    }

    private BillingUsage usageFor(String consumerCode, List<ServiceInvokeLog> logs) {
        long elapsed = logs.stream().mapToLong(ServiceInvokeLog::elapsedMillis).sum();
        return new BillingUsage(TargetType.CONSUMER, (long) Math.abs(consumerCode.hashCode()), null, consumerCode,
                logs.size(), elapsed, logs.stream().map(ServiceInvokeLog::serviceCode).distinct().count(),
                Math.max(1, elapsed / 1000), 0);
    }
}