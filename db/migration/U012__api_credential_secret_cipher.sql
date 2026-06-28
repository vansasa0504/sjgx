-- Rollback V012__api_credential_secret_cipher.sql.

DROP INDEX idx_api_credential_rotated_from ON t_api_credential;
DROP INDEX idx_api_credential_status ON t_api_credential;

ALTER TABLE t_api_credential DROP COLUMN rotated_from;
ALTER TABLE t_api_credential DROP COLUMN status;
ALTER TABLE t_api_credential DROP COLUMN secret_hash;
ALTER TABLE t_api_credential DROP COLUMN secret_cipher;
