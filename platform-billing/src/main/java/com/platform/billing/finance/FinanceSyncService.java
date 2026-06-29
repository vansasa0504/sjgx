package com.platform.billing.finance;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillRepository;
import com.platform.billing.model.BillStatus;
import com.platform.common.audit.AuditEvent;
import com.platform.common.audit.AuditLogRepository;
import com.platform.common.audit.AuditStatus;
import com.platform.common.exception.BusinessException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class FinanceSyncService {
    private static final EnumSet<BillStatus> SYNCABLE_STATUS =
            EnumSet.of(BillStatus.CONFIRMED, BillStatus.ADJUSTED, BillStatus.SETTLED);

    private final BillRepository billRepository;
    private final FinanceSyncRepository syncRepository;
    private final FinanceSystemAdapter financeSystemAdapter;
    private final PurchaseContractAdapter purchaseContractAdapter;
    private final AuditLogRepository auditLogRepository;

    public FinanceSyncService(BillRepository billRepository,
                              FinanceSyncRepository syncRepository,
                              FinanceSystemAdapter financeSystemAdapter,
                              PurchaseContractAdapter purchaseContractAdapter,
                              AuditLogRepository auditLogRepository) {
        this.billRepository = billRepository;
        this.syncRepository = syncRepository;
        this.financeSystemAdapter = financeSystemAdapter;
        this.purchaseContractAdapter = purchaseContractAdapter;
        this.auditLogRepository = auditLogRepository;
    }

    public FinanceSyncRecord sync(String billNo, String adapterType) {
        String type = normalizeAdapterType(adapterType);
        Bill bill = requireSyncableBill(billNo);
        return doSync(bill, type, 0, "SYNC");
    }

    public FinanceSyncRecord retry(String billNo, String adapterType) {
        String type = normalizeAdapterType(adapterType);
        Bill bill = requireSyncableBill(billNo);
        FinanceSyncRecord failed = syncRepository.findLastFailed(billNo, type)
                .orElseThrow(() -> new BusinessException("FINANCE_SYNC-409", "no failed sync record to retry"));
        return doSync(bill, type, failed.retryCount() + 1, "RETRY");
    }

    public List<FinanceSyncRecord> list(String billNo) {
        requireBill(billNo);
        return syncRepository.findByBillNo(billNo);
    }

    private FinanceSyncRecord doSync(Bill bill, String adapterType, int retryCount, String action) {
        String traceId = UUID.randomUUID().toString();
        FinanceSyncResult result;
        try {
            result = callAdapter(adapterType, bill);
        } catch (Exception ex) {
            result = new FinanceSyncResult(false, null, ex.getMessage());
        }
        String status = result.success() ? "SUCCESS" : "FAILED";
        FinanceSyncRecord saved = syncRepository.save(new FinanceSyncRecord(0, bill.billNo(), adapterType,
                result.externalNo(), status, retryCount, result.message(), Instant.now()));
        appendAudit(traceId, bill.billNo(), action, result.success() ? AuditStatus.SUCCESS : AuditStatus.FAILED,
                "adapterType=" + adapterType + ",status=" + status + ",externalNo=" + nullToEmpty(result.externalNo()));
        return saved;
    }

    private FinanceSyncResult callAdapter(String adapterType, Bill bill) {
        if ("FINANCE".equals(adapterType)) {
            return financeSystemAdapter.sync(bill);
        }
        return purchaseContractAdapter.sync(bill);
    }

    private Bill requireSyncableBill(String billNo) {
        Bill bill = requireBill(billNo);
        if (!SYNCABLE_STATUS.contains(bill.status())) {
            throw new BusinessException("BILL_STATE_INVALID", "bill state does not allow finance sync: " + bill.status());
        }
        return bill;
    }

    private Bill requireBill(String billNo) {
        return billRepository.findByBillNo(billNo)
                .orElseThrow(() -> new BusinessException("BILL-404", "bill not found: " + billNo));
    }

    private String normalizeAdapterType(String adapterType) {
        if (adapterType == null || adapterType.isBlank()) {
            return "FINANCE";
        }
        String normalized = adapterType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"FINANCE".equals(normalized) && !"PURCHASE".equals(normalized)) {
            throw new BusinessException("FINANCE_SYNC-400", "invalid adapterType: " + adapterType);
        }
        return normalized;
    }

    private void appendAudit(String traceId, String billNo, String action, AuditStatus status, String detail) {
        auditLogRepository.append(new AuditEvent(null, traceId, "BILL_SYNC_TO_FINANCE", "SYSTEM", "system",
                "BILL", billNo, action, detail, "", "", status, Instant.now()));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
