package com.platform.partner;

import com.platform.common.exception.BusinessException;

import java.util.Map;

public class PartnerStateMachine {
    private static final Map<PartnerStatus, Map<PartnerEvent, PartnerStatus>> TRANSITIONS = Map.of(
            PartnerStatus.REGISTERED, Map.of(PartnerEvent.SUBMIT, PartnerStatus.SUBMITTED),
            PartnerStatus.SUBMITTED, Map.of(PartnerEvent.APPROVE, PartnerStatus.APPROVED, PartnerEvent.REJECT, PartnerStatus.REJECTED),
            PartnerStatus.APPROVED, Map.of(PartnerEvent.ADMIT, PartnerStatus.ADMITTED, PartnerEvent.REJECT, PartnerStatus.REJECTED),
            PartnerStatus.ADMITTED, Map.of(PartnerEvent.RATE, PartnerStatus.RATED, PartnerEvent.SUSPEND, PartnerStatus.SUSPENDED, PartnerEvent.EXIT, PartnerStatus.EXITED),
            PartnerStatus.RATED, Map.of(PartnerEvent.SUSPEND, PartnerStatus.SUSPENDED, PartnerEvent.EXIT, PartnerStatus.EXITED),
            PartnerStatus.SUSPENDED, Map.of(PartnerEvent.RESUME, PartnerStatus.ADMITTED, PartnerEvent.EXIT, PartnerStatus.EXITED)
    );

    public PartnerStatus transit(PartnerStatus current, PartnerEvent event) {
        PartnerStatus next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw new BusinessException("PARTNER-409", "illegal partner state transition");
        }
        return next;
    }
}
