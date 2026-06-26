package com.platform.billing.dashboard;

import com.platform.billing.bill.Bill;
import com.platform.billing.stats.MetricName;
import com.platform.billing.stats.StatsSnapshot;
import java.math.BigDecimal;
import java.util.List;

public class DashboardService {
    public DashboardSummary summarize(List<StatsSnapshot> snapshots, List<Bill> bills) {
        long invokeCount = snapshots.stream()
                .filter(snapshot -> snapshot.metricName() == MetricName.INVOKE_COUNT)
                .mapToLong(snapshot -> snapshot.metricValue().longValue())
                .sum();
        BigDecimal successRate = snapshots.stream()
                .filter(snapshot -> snapshot.metricName() == MetricName.SUCCESS_RATE)
                .map(StatsSnapshot::metricValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        long serviceCount = snapshots.stream()
                .filter(snapshot -> snapshot.dimensionId() != null)
                .map(StatsSnapshot::dimensionId)
                .distinct()
                .count();
        if (serviceCount == 0 && invokeCount > 0) {
            serviceCount = 1;
        }
        BigDecimal cost = bills.stream().map(Bill::totalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DashboardSummary(serviceCount, invokeCount, successRate, BigDecimal.valueOf(100), cost);
    }
}

