CREATE TABLE t_partner (
    id BIGINT PRIMARY KEY,
    partner_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    data_type VARCHAR(64),
    industry_type VARCHAR(64),
    compliance_level VARCHAR(32),
    service_quality_level VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    rating VARCHAR(8),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE t_partner_interface (
    id BIGINT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    data_scope VARCHAR(512),
    rate_limit_per_minute BIGINT,
    credential_cipher VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE t_partner_event (
    id BIGINT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    event VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
