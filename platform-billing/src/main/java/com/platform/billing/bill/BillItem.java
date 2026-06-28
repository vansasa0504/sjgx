package com.platform.billing.bill;

import java.math.BigDecimal;
import java.time.Instant;

public record BillItem(
        Long id,
        Long billId,
        String billNo,
        String itemType,
        String refId,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String period,
        String serviceCode,
        String consumerCode,
        String partnerCode,
        Instant createdAt
) {
}
