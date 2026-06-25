package com.platform.billing.finance;

import com.platform.billing.bill.Bill;

public class MockFinanceSystemAdapter implements FinanceSystemAdapter {
    @Override
    public FinanceSyncResult sync(Bill bill) {
        return new FinanceSyncResult(true, "FIN-" + bill.billNo(), "mock synced");
    }
}