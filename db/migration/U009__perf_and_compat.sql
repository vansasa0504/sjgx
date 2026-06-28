-- Rollback V009__perf_and_compat.sql.

DROP INDEX idx_audit_log_created_at ON t_audit_log;
DROP INDEX idx_invoke_log_service_created ON t_service_invoke_log;
DROP INDEX idx_invoke_log_service_consumer ON t_service_invoke_log;
DROP INDEX idx_invoke_log_created_at ON t_service_invoke_log;

ALTER TABLE t_service_invoke_log DROP COLUMN response_size;
