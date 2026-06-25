package com.platform.partner.consumer;

import com.platform.common.exception.BusinessException;

import java.util.Map;

public class ConsumerStateMachine {
    private static final Map<ConsumerStatus, Map<ConsumerEvent, ConsumerStatus>> TRANSITIONS = Map.of(
            ConsumerStatus.REGISTERED, Map.of(ConsumerEvent.SUBMIT, ConsumerStatus.SUBMITTED),
            ConsumerStatus.SUBMITTED, Map.of(ConsumerEvent.APPROVE, ConsumerStatus.APPROVED),
            ConsumerStatus.APPROVED, Map.of(ConsumerEvent.CONFIGURE_QUOTA, ConsumerStatus.QUOTA_CONFIGURED),
            ConsumerStatus.QUOTA_CONFIGURED, Map.of(ConsumerEvent.ENABLE, ConsumerStatus.ENABLED),
            ConsumerStatus.ENABLED, Map.of(ConsumerEvent.SUSPEND, ConsumerStatus.SUSPENDED, ConsumerEvent.CANCEL, ConsumerStatus.CANCELLED),
            ConsumerStatus.SUSPENDED, Map.of(ConsumerEvent.RESUME, ConsumerStatus.ENABLED, ConsumerEvent.CANCEL, ConsumerStatus.CANCELLED)
    );

    public ConsumerStatus transit(ConsumerStatus current, ConsumerEvent event) {
        ConsumerStatus next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw new BusinessException("CONSUMER-409", "illegal consumer state transition");
        }
        return next;
    }
}
