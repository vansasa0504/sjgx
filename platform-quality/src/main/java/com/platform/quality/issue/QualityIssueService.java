package com.platform.quality.issue;

import com.platform.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class QualityIssueService {
    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, QualityIssue> issues = new ConcurrentHashMap<>();
    private final QualityIssueStateMachine stateMachine = new QualityIssueStateMachine();

    public QualityIssue open(String targetType, String description) {
        return open(null, null, QualityIssueType.ANOMALY, QualitySeverity.ERROR, description);
    }

    public QualityIssue open(Long checkResultId, Long ruleId, QualityIssueType issueType, QualitySeverity severity, String description) {
        QualityIssue issue = new QualityIssue(ids.getAndIncrement(), checkResultId, ruleId, issueType, severity, description);
        issues.put(issue.id(), issue);
        return issue;
    }

    public QualityIssue assign(long id, String assignee) {
        QualityIssue issue = require(id);
        issue.status(stateMachine.transit(issue.status(), QualityIssueEvent.ASSIGN));
        issue.assignee(assignee);
        return issue;
    }

    public QualityIssue resolve(long id, String resolution) {
        QualityIssue issue = require(id);
        issue.resolution(resolution);
        return issue;
    }

    public QualityIssue apply(long id, QualityIssueEvent event) {
        QualityIssue issue = require(id);
        issue.status(stateMachine.transit(issue.status(), event));
        return issue;
    }

    public List<QualityIssue> list() {
        return new ArrayList<>(issues.values());
    }

    private QualityIssue require(long id) {
        QualityIssue issue = issues.get(id);
        if (issue == null) {
            throw new BusinessException("QUALITY-404", "quality issue not found");
        }
        return issue;
    }
}
