CREATE TABLE IF NOT EXISTS t_regulatory_report (
    id BIGINT PRIMARY KEY,
    report_type VARCHAR(32) NOT NULL,
    period_from TIMESTAMP NULL,
    period_to TIMESTAMP NULL,
    content CLOB NOT NULL,
    status VARCHAR(32) NOT NULL,
    receipt_no VARCHAR(128),
    receipt_message VARCHAR(512),
    generated_at TIMESTAMP NOT NULL,
    submitted_at TIMESTAMP NULL
);

CREATE INDEX idx_regulatory_report_type ON t_regulatory_report(report_type, generated_at);
