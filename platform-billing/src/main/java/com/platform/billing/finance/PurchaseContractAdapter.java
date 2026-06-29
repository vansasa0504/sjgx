package com.platform.billing.finance;

import com.platform.billing.bill.Bill;

public interface PurchaseContractAdapter {
    FinanceSyncResult sync(Bill bill);
}
