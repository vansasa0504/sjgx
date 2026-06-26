DROP INDEX IF EXISTS idx_audit_log_created_at;
DROP INDEX IF EXISTS idx_invoke_log_service_created;
DROP INDEX IF EXISTS idx_invoke_log_service_consumer;
DROP INDEX IF EXISTS idx_invoke_log_created_at;
ALTER TABLE t_service_invoke_log DROP COLUMN IF EXISTS response_size;
