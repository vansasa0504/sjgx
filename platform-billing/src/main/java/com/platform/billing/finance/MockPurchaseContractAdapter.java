package com.platform.billing.finance;

import com.platform.billing.bill.Bill;

public class MockPurchaseContractAdapter implements PurchaseContractAdapter {
    @Override
    public FinanceSyncResult sync(Bill bill) {
        return new FinanceSyncResult(true, "PUR-" + bill.billNo(), "mock purchase synced");
    }
}
