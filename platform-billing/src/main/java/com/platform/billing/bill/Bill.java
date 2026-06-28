package com.platform.billing.bill;

import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Bill(
        Long id,
        String billNo,
        BillType billType,
        BillPeriod billPeriod,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalAmount,
        BillStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<BillItem> items
) {
    public Bill(Long id, String billNo, BillType billType, BillPeriod billPeriod, LocalDate periodStart,
                LocalDate periodEnd, BigDecimal totalAmount, BillStatus status, Instant createdAt, Instant updatedAt) {
        this(id, billNo, billType, billPeriod, periodStart, periodEnd, totalAmount, status, createdAt, updatedAt, List.of());
    }

    public Bill {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
