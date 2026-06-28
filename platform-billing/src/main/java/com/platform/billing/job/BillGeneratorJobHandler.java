package com.platform.billing.job;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillGenerator;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillType;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.LocalDate;

public class BillGeneratorJobHandler {
    private final BillGenerator billGenerator;

    public BillGeneratorJobHandler(BillGenerator billGenerator) {
        this.billGenerator = billGenerator;
    }

    public Bill execute(BillType billType, BillPeriod period, LocalDate start, LocalDate end) {
        return billGenerator.generate(billType, period, start, end);
    }

    @XxlJob("billGenerate")
    public void billGenerate() {
        LocalDate today = LocalDate.now();
        execute(BillType.EXPENSE, BillPeriod.DAILY, today, today);
    }
}
