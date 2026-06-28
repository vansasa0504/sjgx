CREATE TABLE t_data_catalog (
    id BIGINT PRIMARY KEY,
    catalog_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    subject VARCHAR(64) NOT NULL,
    partner_id BIGINT NOT NULL,
    data_type VARCHAR(64) NOT NULL,
    scenario VARCHAR(64),
    field_definitions CLOB,
    format VARCHAR(32),
    update_frequency VARCHAR(64),
    source VARCHAR(128),
    compliance_note CLOB,
    usage_limit CLOB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
