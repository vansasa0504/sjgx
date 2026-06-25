package com.platform.billing.bill;

import java.util.List;
import java.util.Optional;

public interface BillRepository {
    Bill save(Bill bill);

    Optional<Bill> findByBillNo(String billNo);

    List<Bill> findAll();
}