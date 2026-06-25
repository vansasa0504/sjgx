package com.platform.pipeline.service;

import com.platform.common.exception.BusinessException;

import java.util.Map;

public class DataServiceStateMachine {
    private static final Map<DataServiceStatus, Map<DataServiceEvent, DataServiceStatus>> TRANSITIONS = Map.of(
            DataServiceStatus.REGISTERED, Map.of(DataServiceEvent.DEFINE, DataServiceStatus.DEFINED),
            DataServiceStatus.DEFINED, Map.of(DataServiceEvent.TEST, DataServiceStatus.TESTED),
            DataServiceStatus.TESTED, Map.of(DataServiceEvent.PUBLISH, DataServiceStatus.PUBLISHED),
            DataServiceStatus.PUBLISHED, Map.of(DataServiceEvent.VERSION, DataServiceStatus.VERSIONED, DataServiceEvent.OFFLINE, DataServiceStatus.OFFLINE),
            DataServiceStatus.VERSIONED, Map.of(DataServiceEvent.PUBLISH, DataServiceStatus.PUBLISHED, DataServiceEvent.OFFLINE, DataServiceStatus.OFFLINE)
    );

    public DataServiceStatus transit(DataServiceStatus current, DataServiceEvent event) {
        DataServiceStatus next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw new BusinessException("SERVICE-409", "illegal data service state transition");
        }
        return next;
    }
}
