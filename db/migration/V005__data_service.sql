CREATE TABLE t_data_service (
    id BIGINT PRIMARY KEY,
    service_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    route_key VARCHAR(128) NOT NULL,
    version_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE t_service_invoke_log (
    id BIGINT PRIMARY KEY,
    service_code VARCHAR(64) NOT NULL,
    consumer_code VARCHAR(64) NOT NULL,
    status_code INT NOT NULL,
    elapsed_millis BIGINT NOT NULL,
    log_day VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
