package com.platform.billing.bill;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryBillItemRepository implements BillItemRepository {
    private final AtomicLong ids = new AtomicLong(1);
    private final List<BillItem> items = new CopyOnWriteArrayList<>();

    @Override
    public List<BillItem> saveAll(String billNo, Long billId, List<BillItem> newItems) {
        items.removeIf(item -> item.billNo().equals(billNo));
        Instant now = Instant.now();
        List<BillItem> saved = newItems.stream()
                .map(item -> new BillItem(item.id() == null ? ids.getAndIncrement() : item.id(),
                        billId, billNo, item.itemType(), item.refId(), item.quantity(), item.unitPrice(),
                        item.amount(), item.period(), item.serviceCode(), item.consumerCode(), item.partnerCode(),
                        item.createdAt() == null ? now : item.createdAt()))
                .toList();
        items.addAll(saved);
        return saved;
    }

    @Override
    public List<BillItem> findByBillNo(String billNo) {
        return items.stream().filter(item -> item.billNo().equals(billNo)).toList();
    }

    @Override
    public List<BillItem> findAll() {
        return List.copyOf(items);
    }
}
