DROP TABLE IF EXISTS t_user_certificate;
ALTER TABLE t_user DROP COLUMN mfa_last_counter;
ALTER TABLE t_user DROP COLUMN mfa_enabled;
ALTER TABLE t_user DROP COLUMN mfa_secret_cipher;
