-- Rollback V013__service_invoke_log_fact_source.sql.

DROP INDEX idx_invoke_log_consumer_created ON t_service_invoke_log;
DROP INDEX idx_invoke_log_request_hash ON t_service_invoke_log;
DROP INDEX idx_invoke_log_trace ON t_service_invoke_log;

ALTER TABLE t_service_invoke_log DROP COLUMN error_message;
ALTER TABLE t_service_invoke_log DROP COLUMN error_code;
ALTER TABLE t_service_invoke_log DROP COLUMN request_hash;
ALTER TABLE t_service_invoke_log DROP COLUMN api_key;
ALTER TABLE t_service_invoke_log DROP COLUMN partner_code;
ALTER TABLE t_service_invoke_log DROP COLUMN trace_id;
