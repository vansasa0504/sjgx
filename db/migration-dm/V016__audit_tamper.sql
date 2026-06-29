ALTER TABLE t_audit_log ADD COLUMN prev_hash VARCHAR(64);
ALTER TABLE t_audit_log ADD COLUMN hash VARCHAR(64);

CREATE INDEX idx_audit_hash ON t_audit_log(hash);

-- Production permission hardening:
-- REVOKE UPDATE, DELETE ON t_audit_log FROM <business_user>;
-- The concrete database user is environment-specific, so the executable
-- migration only adds the verifiable hash chain columns. Deployment runbooks
-- must apply the REVOKE to the application account.
