package com.platform.billing.bill;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryBillRepository implements BillRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final List<Bill> bills = new CopyOnWriteArrayList<>();

    @Override
    public Bill save(Bill bill) {
        Bill saved = new Bill(bill.id() == null ? ids.getAndIncrement() : bill.id(), bill.billNo(), bill.billType(),
                bill.billPeriod(), bill.periodStart(), bill.periodEnd(), bill.totalAmount(), bill.status(),
                bill.createdAt(), bill.updatedAt());
        bills.removeIf(existing -> existing.billNo().equals(saved.billNo()));
        bills.add(saved);
        return saved;
    }

    @Override
    public Optional<Bill> findByBillNo(String billNo) {
        return bills.stream().filter(bill -> bill.billNo().equals(billNo)).findFirst();
    }

    @Override
    public List<Bill> findAll() {
        return List.copyOf(bills);
    }
}