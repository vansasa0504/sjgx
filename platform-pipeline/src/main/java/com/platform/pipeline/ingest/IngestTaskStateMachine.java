package com.platform.pipeline.ingest;

import com.platform.common.exception.BusinessException;

import java.util.Map;

public class IngestTaskStateMachine {
    private static final Map<IngestTaskStatus, Map<IngestTaskEvent, IngestTaskStatus>> TRANSITIONS = Map.of(
            IngestTaskStatus.DRAFT, Map.of(IngestTaskEvent.START_TEST, IngestTaskStatus.TESTING),
            IngestTaskStatus.TESTING, Map.of(IngestTaskEvent.SUBMIT_APPROVAL, IngestTaskStatus.PENDING_APPROVAL, IngestTaskEvent.FAIL_TEST, IngestTaskStatus.DRAFT),
            IngestTaskStatus.PENDING_APPROVAL, Map.of(IngestTaskEvent.APPROVE, IngestTaskStatus.ONLINE, IngestTaskEvent.REJECT, IngestTaskStatus.DRAFT),
            IngestTaskStatus.ONLINE, Map.of(IngestTaskEvent.OFFLINE, IngestTaskStatus.OFFLINE)
    );

    public IngestTaskStatus transit(IngestTaskStatus current, IngestTaskEvent event) {
        IngestTaskStatus next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw new BusinessException("INGEST-409", "illegal ingest task state transition");
        }
        return next;
    }
}
