ALTER TABLE t_lifecycle_record ADD COLUMN operator VARCHAR(64);
ALTER TABLE t_lifecycle_record ADD COLUMN reason VARCHAR(256);
ALTER TABLE t_lifecycle_record ADD COLUMN proof_hash VARCHAR(64);
ALTER TABLE t_lifecycle_record ADD COLUMN object_key VARCHAR(256);
