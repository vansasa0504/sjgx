-- P2-01 large table partition baseline for DM8.
-- DM partition syntax must be validated on a real DM8 environment before production rollout.

ALTER TABLE t_service_invoke_log DROP PRIMARY KEY;
ALTER TABLE t_service_invoke_log ADD PRIMARY KEY (id, created_at);

ALTER TABLE t_audit_log DROP PRIMARY KEY;
ALTER TABLE t_audit_log ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE t_audit_log ADD PRIMARY KEY (id, created_at);

ALTER TABLE t_raw_data DROP PRIMARY KEY;
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
    created_at TIMESTAMP NOT NULL,
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
    detail CLOB,
    source_ip VARCHAR(64),
    user_agent VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    prev_hash VARCHAR(64),
    hash VARCHAR(64),
    PRIMARY KEY (id, created_at)
);

CREATE INDEX idx_audit_archive_trace ON t_audit_log_archive(trace_id);
CREATE INDEX idx_audit_archive_event_type_created ON t_audit_log_archive(event_type, created_at);
CREATE INDEX idx_audit_archive_hash ON t_audit_log_archive(hash);
CREATE UNIQUE INDEX uk_audit_archive_id ON t_audit_log_archive(id);

-- Real DM8 rollout should apply:
-- ALTER TABLE t_service_invoke_log PARTITION BY RANGE (created_at) (... DATE '2026-02-01' ...);
-- ALTER TABLE t_audit_log PARTITION BY RANGE (created_at) (... DATE '2026-02-01' ...);
-- ALTER TABLE t_raw_data PARTITION BY RANGE (created_at) (... DATE '2026-02-01' ...);
