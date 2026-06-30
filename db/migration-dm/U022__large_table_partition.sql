-- Real DM8 rollback should remove range partitions before restoring single-column keys.

ALTER TABLE t_service_invoke_log DROP PRIMARY KEY;
ALTER TABLE t_service_invoke_log ADD PRIMARY KEY (id);

ALTER TABLE t_audit_log DROP PRIMARY KEY;
ALTER TABLE t_audit_log ADD PRIMARY KEY (id);

ALTER TABLE t_raw_data DROP PRIMARY KEY;
ALTER TABLE t_raw_data ADD PRIMARY KEY (id);

DROP TABLE IF EXISTS t_service_invoke_log_archive;
DROP TABLE IF EXISTS t_audit_log_archive;
