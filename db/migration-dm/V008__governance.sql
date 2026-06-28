CREATE TABLE t_billing_rule (
    id BIGINT PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    rule_name VARCHAR(128) NOT NULL,
    billing_model VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT,
    unit_price DECIMAL(12,6) NOT NULL,
    currency VARCHAR(16) DEFAULT 'CNY',
    effective_from DATE NOT NULL,
    effective_to DATE,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_bill (
    id BIGINT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL UNIQUE,
    bill_type VARCHAR(32) NOT NULL,
    bill_period VARCHAR(32) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount DECIMAL(16,4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_stats_snapshot (
    id BIGINT PRIMARY KEY,
    metric_name VARCHAR(64) NOT NULL,
    dimension VARCHAR(32) NOT NULL,
    dimension_id BIGINT,
    metric_value DECIMAL(20,4) NOT NULL,
    snapshot_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_snapshot ON t_stats_snapshot(metric_name, snapshot_at);

CREATE TABLE t_audit_log (
    id BIGINT PRIMARY KEY,
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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trace ON t_audit_log(trace_id);
CREATE INDEX idx_event_type_created ON t_audit_log(event_type, created_at);
CREATE INDEX idx_actor ON t_audit_log(actor_type, actor_id);

-- t_audit_log is append-only at application DAO level. Production DM/OceanBase
-- deployments may add dialect-specific triggers to reject UPDATE and DELETE.
