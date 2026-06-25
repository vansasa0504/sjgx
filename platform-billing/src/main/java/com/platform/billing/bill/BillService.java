package com.platform.billing.bill;

import com.platform.billing.model.BillStatus;
import java.time.Instant;

public class BillService {
    private final BillRepository repository;
    private final BillStateMachine stateMachine;

    public BillService(BillRepository repository, BillStateMachine stateMachine) {
        this.repository = repository;
        this.stateMachine = stateMachine;
    }

    public Bill changeStatus(String billNo, BillStatus next) {
        Bill bill = repository.findByBillNo(billNo).orElseThrow();
        BillStatus status = stateMachine.transition(bill.status(), next);
        return repository.save(new Bill(bill.id(), bill.billNo(), bill.billType(), bill.billPeriod(), bill.periodStart(),
                bill.periodEnd(), bill.totalAmount(), status, bill.createdAt(), Instant.now()));
    }

    public Bill confirm(String billNo) {
        return changeStatus(billNo, BillStatus.CONFIRMED);
    }

    public Bill dispute(String billNo) {
        return changeStatus(billNo, BillStatus.DISPUTED);
    }

    public Bill adjust(String billNo) {
        return changeStatus(billNo, BillStatus.ADJUSTED);
    }

    public Bill settle(String billNo) {
        return changeStatus(billNo, BillStatus.SETTLED);
    }
}