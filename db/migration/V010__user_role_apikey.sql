-- V010: evolve identity tables and add API credential storage.

ALTER TABLE t_user ADD COLUMN updated_at TIMESTAMP;

ALTER TABLE t_role ADD COLUMN created_at TIMESTAMP;

CREATE TABLE t_user_permission (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX uk_user_perm ON t_user_permission(user_id, permission_code);
CREATE INDEX idx_user_perm_user ON t_user_permission(user_id);

CREATE TABLE t_role_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX uk_role_perm ON t_role_permission(role_id, permission_code);
CREATE INDEX idx_role_perm_role ON t_role_permission(role_id);

CREATE TABLE t_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL
);

CREATE UNIQUE INDEX uk_user_role ON t_user_role(user_id, role_id);
CREATE INDEX idx_user_role_user ON t_user_role(user_id);
CREATE INDEX idx_user_role_role ON t_user_role(role_id);

CREATE TABLE t_api_credential (
    id BIGINT PRIMARY KEY,
    api_key VARCHAR(128) NOT NULL UNIQUE,
    secret VARCHAR(256) NOT NULL,
    consumer_code VARCHAR(64) NOT NULL,
    service_code VARCHAR(128),
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_api_credential_consumer ON t_api_credential(consumer_code);
CREATE INDEX idx_api_credential_service ON t_api_credential(service_code);
