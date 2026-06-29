DROP INDEX idx_audit_hash ON t_audit_log;

ALTER TABLE t_audit_log DROP COLUMN hash;
ALTER TABLE t_audit_log DROP COLUMN prev_hash;

-- Rollback counterpart for production permission hardening:
-- GRANT UPDATE, DELETE ON t_audit_log TO <business_user>;
