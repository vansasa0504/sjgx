CREATE TABLE t_ingest_checkpoint (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    connector_type VARCHAR(32) NOT NULL,
    offset_value BIGINT NOT NULL,
    checkpoint_json VARCHAR(512),
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_checkpoint_task_connector ON t_ingest_checkpoint(task_id, connector_type);
CREATE INDEX idx_checkpoint_task ON t_ingest_checkpoint(task_id);
