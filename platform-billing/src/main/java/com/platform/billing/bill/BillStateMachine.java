package com.platform.billing.bill;

import com.platform.billing.model.BillStatus;
import com.platform.common.exception.BusinessException;
import java.util.EnumMap;
import java.util.Set;

public class BillStateMachine {
    private final EnumMap<BillStatus, Set<BillStatus>> transitions = new EnumMap<>(BillStatus.class);

    public BillStateMachine() {
        transitions.put(BillStatus.GENERATED, Set.of(BillStatus.CONFIRMED, BillStatus.DISPUTED));
        transitions.put(BillStatus.CONFIRMED, Set.of(BillStatus.SETTLED));
        transitions.put(BillStatus.DISPUTED, Set.of(BillStatus.ADJUSTED));
        transitions.put(BillStatus.ADJUSTED, Set.of(BillStatus.CONFIRMED, BillStatus.SETTLED));
        transitions.put(BillStatus.SETTLED, Set.of());
    }

    public BillStatus transition(BillStatus current, BillStatus next) {
        if (!transitions.getOrDefault(current, Set.of()).contains(next)) {
            throw new BusinessException("BILL_STATE_INVALID", current + " cannot transition to " + next);
        }
        return next;
    }
}