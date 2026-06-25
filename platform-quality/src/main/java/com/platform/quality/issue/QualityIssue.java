package com.platform.quality.issue;

import java.time.Instant;

public class QualityIssue {
    private final long id;
    private final Long checkResultId;
    private final Long ruleId;
    private final QualityIssueType issueType;
    private final QualitySeverity severity;
    private final String description;
    private QualityIssueStatus status;
    private String assignee;
    private String resolution;
    private final Instant createdAt;
    private Instant updatedAt;

    public QualityIssue(long id, String targetType, String description) {
        this(id, null, null, QualityIssueType.ANOMALY, QualitySeverity.ERROR, description);
    }

    public QualityIssue(long id, Long checkResultId, Long ruleId, QualityIssueType issueType, QualitySeverity severity, String description) {
        this.id = id;
        this.checkResultId = checkResultId;
        this.ruleId = ruleId;
        this.issueType = issueType;
        this.severity = severity;
        this.description = description;
        this.status = QualityIssueStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public long id() { return id; }
    public Long checkResultId() { return checkResultId; }
    public Long ruleId() { return ruleId; }
    public QualityIssueType issueType() { return issueType; }
    public QualitySeverity severity() { return severity; }
    public String targetType() { return "QUALITY"; }
    public String description() { return description; }
    public QualityIssueStatus status() { return status; }
    public String assignee() { return assignee; }
    public String resolution() { return resolution; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    void status(QualityIssueStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    void assignee(String assignee) { this.assignee = assignee; this.updatedAt = Instant.now(); }
    void resolution(String resolution) { this.resolution = resolution; this.updatedAt = Instant.now(); }
}
