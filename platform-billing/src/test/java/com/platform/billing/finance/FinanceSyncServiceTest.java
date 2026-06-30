package com.platform.billing.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.InMemoryBillRepository;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillStatus;
import com.platform.billing.model.BillType;
import com.platform.common.audit.InMemoryAuditLogRepository;
import com.platform.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinanceSyncServiceTest {
    @Test
    void syncSuccessFailureRetryAndPurchaseAdapter() {
        InMemoryBillRepository bills = new InMemoryBillRepository();
        InMemoryFinanceSyncRepository records = new InMemoryFinanceSyncRepository();
        InMemoryAuditLogRepository audit = new InMemoryAuditLogRepository();
        bills.save(bill("BILL-S", BillStatus.CONFIRMED));
        MutableFinanceAdapter finance = new MutableFinanceAdapter(false);
        FinanceSyncService service = new FinanceSyncService(bills, records, finance,
                bill -> new FinanceSyncResult(true, "PUR-" + bill.billNo(), "purchase ok"), audit);

        FinanceSyncRecord failed = service.sync("BILL-S", "FINANCE");
        assertEquals("FAILED", failed.status());
        assertEquals(0, failed.retryCount());

        finance.success = true;
        FinanceSyncRecord retried = service.retry("BILL-S", "FINANCE");
        assertEquals("SUCCESS", retried.status());
        assertEquals(1, retried.retryCount());
        assertEquals("FIN-BILL-S", retried.externalNo());

        FinanceSyncRecord purchase = service.sync("BILL-S", "PURCHASE");
        assertEquals("SUCCESS", purchase.status());
        assertEquals("PUR-BILL-S", purchase.externalNo());
        assertEquals(3, records.findByBillNo("BILL-S").size());
        assertEquals(3, audit.findByEventType("BILL_SYNC_TO_FINANCE", Instant.EPOCH, Instant.now()).size());
    }

    @Test
    void adapterExceptionIsPersistedAsFailedAndRepeatedRetryCountsIncrement() {
        InMemoryBillRepository bills = new InMemoryBillRepository();
        InMemoryFinanceSyncRepository records = new InMemoryFinanceSyncRepository();
        bills.save(bill("BILL-E", BillStatus.CONFIRMED));
        FinanceSyncService service = new FinanceSyncService(bills, records,
                bill -> { throw new RuntimeException("network down"); },
                bill -> new FinanceSyncResult(true, "PUR-" + bill.billNo(), "ok"),
                new InMemoryAuditLogRepository());

        FinanceSyncRecord first = service.sync("BILL-E", null);
        FinanceSyncRecord second = service.retry("BILL-E", null);
        FinanceSyncRecord third = service.retry("BILL-E", null);

        assertEquals("FAILED", first.status());
        assertEquals("network down", first.message());
        assertEquals(1, second.retryCount());
        assertEquals(2, third.retryCount());
    }

    @Test
    void retryCountIsScopedToLatestFailedSequence() {
        InMemoryBillRepository bills = new InMemoryBillRepository();
        InMemoryFinanceSyncRepository records = new InMemoryFinanceSyncRepository();
        bills.save(bill("BILL-R", BillStatus.CONFIRMED));
        MutableFinanceAdapter finance = new MutableFinanceAdapter(false);
        FinanceSyncService service = new FinanceSyncService(bills, records, finance,
                bill -> new FinanceSyncResult(true, "PUR-" + bill.billNo(), "ok"),
                new InMemoryAuditLogRepository());

        FinanceSyncRecord firstFailed = service.sync("BILL-R", null);
        finance.success = true;
        FinanceSyncRecord firstRetry = service.retry("BILL-R", null);
        finance.success = false;
        FinanceSyncRecord secondFailed = service.sync("BILL-R", null);
        finance.success = true;
        FinanceSyncRecord secondRetry = service.retry("BILL-R", null);

        assertEquals(0, firstFailed.retryCount());
        assertEquals(1, firstRetry.retryCount());
        assertEquals(0, secondFailed.retryCount());
        assertEquals(1, secondRetry.retryCount());
    }

    @Test
    void validatesStateMissingBillAdapterTypeAndRetryPrecondition() {
        InMemoryBillRepository bills = new InMemoryBillRepository();
        bills.save(bill("BILL-G", BillStatus.GENERATED));
        bills.save(bill("BILL-C", BillStatus.CONFIRMED));
        FinanceSyncService service = new FinanceSyncService(bills, new InMemoryFinanceSyncRepository(),
                bill -> new FinanceSyncResult(true, "FIN-" + bill.billNo(), "ok"),
                bill -> new FinanceSyncResult(true, "PUR-" + bill.billNo(), "ok"),
                new InMemoryAuditLogRepository());

        assertCode("BILL-409", () -> service.sync("BILL-G", null));
        assertCode("BILL-404", () -> service.sync("MISSING", null));
        assertCode("FINANCE_SYNC-400", () -> service.sync("BILL-C", "BAD"));
        assertCode("FINANCE_SYNC-409", () -> service.retry("BILL-C", null));
    }

    private void assertCode(String expected, Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, ex.code());
    }

    private Bill bill(String billNo, BillStatus status) {
        return new Bill(null, billNo, BillType.EXPENSE, BillPeriod.MONTHLY,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                BigDecimal.TEN, status, Instant.now(), Instant.now());
    }

    private static class MutableFinanceAdapter implements FinanceSystemAdapter {
        private boolean success;

        MutableFinanceAdapter(boolean success) {
            this.success = success;
        }

        @Override
        public FinanceSyncResult sync(Bill bill) {
            return success
                    ? new FinanceSyncResult(true, "FIN-" + bill.billNo(), "ok")
                    : new FinanceSyncResult(false, null, "rejected");
        }
    }
}
