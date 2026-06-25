CREATE TABLE IF NOT EXISTS t_partner (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    rating VARCHAR(8),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_partner_interface (
    id BIGINT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    endpoint VARCHAR(512) NOT NULL,
    credential_cipher VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS t_partner_event (
    id BIGINT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    event VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
