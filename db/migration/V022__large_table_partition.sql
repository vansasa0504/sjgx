-- P2-01 large table partition baseline.
-- MySQL executes statements inside /*!80000 ... */; H2 treats them as comments for migration compatibility tests.
-- Existing rows are assigned to matching RANGE partitions by MySQL during ALTER TABLE partition conversion.

ALTER TABLE t_service_invoke_log DROP PRIMARY KEY;
ALTER TABLE t_service_invoke_log MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE t_service_invoke_log ADD PRIMARY KEY (id, created_at);

ALTER TABLE t_audit_log DROP PRIMARY KEY;
ALTER TABLE t_audit_log MODIFY created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL;
ALTER TABLE t_audit_log ADD PRIMARY KEY (id, created_at);

ALTER TABLE t_raw_data DROP PRIMARY KEY;
ALTER TABLE t_raw_data MODIFY created_at DATETIME(6) NOT NULL;
ALTER TABLE t_raw_data ADD PRIMARY KEY (id, created_at);

CREATE TABLE t_service_invoke_log_archive (
    id BIGINT NOT NULL,
    trace_id VARCHAR(64),
    service_code VARCHAR(64) NOT NULL,
    consumer_code VARCHAR(64) NOT NULL,
    partner_code VARCHAR(64),
    api_key VARCHAR(128),
    request_hash VARCHAR(128),
    status_code INT NOT NULL,
    elapsed_millis BIGINT NOT NULL,
    response_size BIGINT DEFAULT 0 NOT NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    log_day VARCHAR(8) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id, created_at)
);

CREATE INDEX idx_invoke_log_archive_created_at ON t_service_invoke_log_archive(created_at);
CREATE INDEX idx_invoke_log_archive_service_created ON t_service_invoke_log_archive(service_code, created_at);
CREATE INDEX idx_invoke_log_archive_consumer_created ON t_service_invoke_log_archive(consumer_code, created_at);
CREATE UNIQUE INDEX uk_invoke_log_archive_id ON t_service_invoke_log_archive(id);

CREATE TABLE t_audit_log_archive (
    id BIGINT NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    detail TEXT,
    source_ip VARCHAR(64),
    user_agent VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    prev_hash VARCHAR(64),
    hash VARCHAR(64),
    PRIMARY KEY (id, created_at)
);

CREATE INDEX idx_audit_archive_trace ON t_audit_log_archive(trace_id);
CREATE INDEX idx_audit_archive_event_type_created ON t_audit_log_archive(event_type, created_at);
CREATE INDEX idx_audit_archive_hash ON t_audit_log_archive(hash);
CREATE UNIQUE INDEX uk_audit_archive_id ON t_audit_log_archive(id);

/*!80000 ALTER TABLE t_service_invoke_log
PARTITION BY RANGE COLUMNS (created_at) (
    PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
    PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
    PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
    PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
    PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
    PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
    PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax VALUES LESS THAN MAXVALUE
) */;

/*!80000 ALTER TABLE t_audit_log
PARTITION BY RANGE COLUMNS (created_at) (
    PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
    PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
    PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
    PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
    PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
    PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
    PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax VALUES LESS THAN MAXVALUE
) */;

/*!80000 ALTER TABLE t_raw_data
PARTITION BY RANGE COLUMNS (created_at) (
    PARTITION p202601 VALUES LESS THAN ('2026-02-01'),
    PARTITION p202602 VALUES LESS THAN ('2026-03-01'),
    PARTITION p202603 VALUES LESS THAN ('2026-04-01'),
    PARTITION p202604 VALUES LESS THAN ('2026-05-01'),
    PARTITION p202605 VALUES LESS THAN ('2026-06-01'),
    PARTITION p202606 VALUES LESS THAN ('2026-07-01'),
    PARTITION p202607 VALUES LESS THAN ('2026-08-01'),
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax VALUES LESS THAN MAXVALUE
) */;
