CREATE TABLE IF NOT EXISTS t_finance_sync_record (
    id BIGINT PRIMARY KEY,
    bill_no VARCHAR(64) NOT NULL,
    adapter_type VARCHAR(32) NOT NULL,
    external_no VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL,
    message VARCHAR(512),
    synced_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_finance_sync_bill ON t_finance_sync_record(bill_no, synced_at);
