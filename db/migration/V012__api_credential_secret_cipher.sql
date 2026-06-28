-- V012: store API credential secrets as ciphertext plus verification hash.
-- Existing plaintext credentials are intentionally invalidated because there is no trusted key
-- to re-encrypt them; recreate credentials after this migration if any legacy rows exist.

ALTER TABLE t_api_credential ADD COLUMN secret_cipher VARCHAR(1024);
ALTER TABLE t_api_credential ADD COLUMN secret_hash VARCHAR(128);
ALTER TABLE t_api_credential ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE';
ALTER TABLE t_api_credential ADD COLUMN rotated_from BIGINT;

UPDATE t_api_credential
SET status = CASE WHEN enabled = 1 THEN 'ACTIVE' ELSE 'DISABLED' END
WHERE status IS NULL;

UPDATE t_api_credential
SET secret = '__SECRET_CIPHER_ONLY__'
WHERE secret <> '__SECRET_CIPHER_ONLY__';

CREATE INDEX idx_api_credential_status ON t_api_credential(status);
CREATE INDEX idx_api_credential_rotated_from ON t_api_credential(rotated_from);
