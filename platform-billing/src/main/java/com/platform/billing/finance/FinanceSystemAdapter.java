package com.platform.billing.finance;

import com.platform.billing.bill.Bill;

public interface FinanceSystemAdapter {
    FinanceSyncResult sync(Bill bill);
}