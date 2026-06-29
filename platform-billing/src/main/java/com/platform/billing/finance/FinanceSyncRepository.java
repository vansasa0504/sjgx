package com.platform.billing.finance;

import java.util.List;
import java.util.Optional;

public interface FinanceSyncRepository {
    FinanceSyncRecord save(FinanceSyncRecord record);

    Optional<FinanceSyncRecord> findLastFailed(String billNo, String adapterType);

    List<FinanceSyncRecord> findByBillNo(String billNo);
}
