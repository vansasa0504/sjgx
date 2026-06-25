package com.platform.quality.issue;

import com.platform.common.exception.BusinessException;

import java.util.EnumMap;
import java.util.Map;

public class QualityIssueStateMachine {
    private final Map<QualityIssueStatus, Map<QualityIssueEvent, QualityIssueStatus>> transitions = new EnumMap<>(QualityIssueStatus.class);

    public QualityIssueStateMachine() {
        transitions.put(QualityIssueStatus.OPEN, Map.of(QualityIssueEvent.ASSIGN, QualityIssueStatus.ASSIGNED));
        transitions.put(QualityIssueStatus.ASSIGNED, Map.of(QualityIssueEvent.START_FIX, QualityIssueStatus.FIXING));
        transitions.put(QualityIssueStatus.FIXING, Map.of(QualityIssueEvent.SUBMIT_VERIFY, QualityIssueStatus.VERIFYING));
        transitions.put(QualityIssueStatus.VERIFYING, Map.of(QualityIssueEvent.CLOSE, QualityIssueStatus.CLOSED));
        transitions.put(QualityIssueStatus.CLOSED, Map.of());
    }

    public QualityIssueStatus transit(QualityIssueStatus current, QualityIssueEvent event) {
        QualityIssueStatus next = transitions.getOrDefault(current, Map.of()).get(event);
        if (next == null) {
            throw new BusinessException("QUALITY-409", "illegal issue transition");
        }
        return next;
    }
}
