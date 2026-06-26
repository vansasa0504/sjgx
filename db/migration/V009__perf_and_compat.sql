ALTER TABLE t_service_invoke_log ADD COLUMN IF NOT EXISTS response_size BIGINT DEFAULT 0 NOT NULL;

CREATE INDEX IF NOT EXISTS idx_invoke_log_created_at ON t_service_invoke_log(created_at);
CREATE INDEX IF NOT EXISTS idx_invoke_log_service_consumer ON t_service_invoke_log(service_code, consumer_code);
CREATE INDEX IF NOT EXISTS idx_invoke_log_service_created ON t_service_invoke_log(service_code, created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON t_audit_log(created_at);
