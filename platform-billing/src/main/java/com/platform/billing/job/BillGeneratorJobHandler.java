package com.platform.billing.job;

import com.platform.billing.bill.Bill;
import com.platform.billing.bill.BillGenerator;
import com.platform.billing.model.BillPeriod;
import com.platform.billing.model.BillType;
import com.platform.common.model.ServiceInvokeLog;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

public class BillGeneratorJobHandler {
    private final BillGenerator billGenerator;
    private final Supplier<List<ServiceInvokeLog>> logSupplier;

    public BillGeneratorJobHandler(BillGenerator billGenerator, Supplier<List<ServiceInvokeLog>> logSupplier) {
        this.billGenerator = billGenerator;
        this.logSupplier = logSupplier;
    }

    public Bill execute(BillType billType, BillPeriod period, LocalDate start, LocalDate end) {
        return billGenerator.generate(billType, period, start, end, logSupplier.get());
    }

    @XxlJob("billGenerate")
    public void billGenerate() {
        LocalDate today = LocalDate.now();
        execute(BillType.EXPENSE, BillPeriod.DAILY, today, today);
    }
}
