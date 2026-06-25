CREATE TABLE IF NOT EXISTS t_ingest_task (
    id BIGINT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    format VARCHAR(32) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_raw_data (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);
