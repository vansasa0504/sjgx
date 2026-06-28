CREATE TABLE t_bill_item (
    id BIGINT PRIMARY KEY,
    bill_id BIGINT NOT NULL,
    bill_no VARCHAR(64) NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    ref_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price DECIMAL(12,6) NOT NULL,
    amount DECIMAL(16,4) NOT NULL,
    period VARCHAR(32) NOT NULL,
    service_code VARCHAR(64),
    consumer_code VARCHAR(64),
    partner_code VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bill_item_bill_no ON t_bill_item(bill_no);
CREATE INDEX idx_bill_item_period ON t_bill_item(period);
CREATE INDEX idx_bill_item_ref ON t_bill_item(item_type, ref_id);
