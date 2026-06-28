ALTER TABLE t_service_invoke_log ADD trace_id VARCHAR(64);
ALTER TABLE t_service_invoke_log ADD partner_code VARCHAR(64);
ALTER TABLE t_service_invoke_log ADD api_key VARCHAR(128);
ALTER TABLE t_service_invoke_log ADD request_hash VARCHAR(128);
ALTER TABLE t_service_invoke_log ADD error_code VARCHAR(64);
ALTER TABLE t_service_invoke_log ADD error_message VARCHAR(512);

CREATE INDEX idx_invoke_log_trace ON t_service_invoke_log(trace_id);
CREATE INDEX idx_invoke_log_request_hash ON t_service_invoke_log(request_hash);
CREATE INDEX idx_invoke_log_consumer_created ON t_service_invoke_log(consumer_code, created_at);
