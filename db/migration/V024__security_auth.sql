-- V024: MFA and certificate authentication state. Secrets/certificates are never stored in plaintext.
ALTER TABLE t_user ADD COLUMN mfa_secret_cipher VARCHAR(512);
ALTER TABLE t_user ADD COLUMN mfa_enabled TINYINT NOT NULL DEFAULT 0;
ALTER TABLE t_user ADD COLUMN mfa_last_counter BIGINT NOT NULL DEFAULT -1;

CREATE TABLE t_user_certificate (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    fingerprint_sha256 VARCHAR(64) NOT NULL UNIQUE,
    subject_cn VARCHAR(256) NOT NULL,
    serial_number VARCHAR(128) NOT NULL,
    certificate_cipher TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    not_before TIMESTAMP NOT NULL,
    not_after TIMESTAMP NOT NULL,
    rotated_from BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_user_certificate_user ON t_user_certificate(user_id, status);
