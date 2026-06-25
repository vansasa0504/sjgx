CREATE TABLE IF NOT EXISTS t_consumer (
    id BIGINT PRIMARY KEY,
    consumer_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    business_line VARCHAR(64),
    system_type VARCHAR(64),
    compliance_level VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_consumer_quota (
    id BIGINT PRIMARY KEY,
    consumer_id BIGINT NOT NULL,
    max_requests BIGINT NOT NULL,
    warn_threshold BIGINT NOT NULL,
    used_requests BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_consumer_event (
    id BIGINT PRIMARY KEY,
    consumer_id BIGINT NOT NULL,
    event VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
