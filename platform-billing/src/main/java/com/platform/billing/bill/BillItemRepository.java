package com.platform.billing.bill;

import java.util.List;

public interface BillItemRepository {
    List<BillItem> saveAll(String billNo, Long billId, List<BillItem> items);

    List<BillItem> findByBillNo(String billNo);

    List<BillItem> findAll();
}
