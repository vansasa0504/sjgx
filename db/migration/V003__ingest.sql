CREATE TABLE t_ingest_task (
    id BIGINT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    format VARCHAR(32) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    sync_mode VARCHAR(32),
    schedule_cron VARCHAR(128),
    mapping_config TEXT,
    rule_config TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE t_raw_data (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    batch_no VARCHAR(64),
    payload TEXT NOT NULL,
    quality_status VARCHAR(32),
    created_at TIMESTAMP NOT NULL
);
