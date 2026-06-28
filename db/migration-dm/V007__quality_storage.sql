CREATE TABLE t_quality_rule (
  id BIGINT PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL UNIQUE,
  rule_name VARCHAR(128) NOT NULL,
  dimension VARCHAR(32) NOT NULL,
  rule_type VARCHAR(32) NOT NULL,
  target_object VARCHAR(64) NOT NULL,
  rule_expression CLOB,
  severity VARCHAR(16) NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_quality_check_result (
  id BIGINT PRIMARY KEY,
  rule_id BIGINT,
  batch_no VARCHAR(64) NOT NULL,
  total_count INT NOT NULL,
  pass_count INT NOT NULL,
  fail_count INT NOT NULL,
  fail_rate DECIMAL(5,4) NOT NULL,
  checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_quality_issue (
  id BIGINT PRIMARY KEY,
  check_result_id BIGINT,
  rule_id BIGINT,
  issue_type VARCHAR(32) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  description VARCHAR(512) NOT NULL,
  status VARCHAR(32) NOT NULL,
  assignee VARCHAR(128),
  resolution VARCHAR(512),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_quality_weight (
  id BIGINT PRIMARY KEY,
  dimension VARCHAR(32) NOT NULL,
  weight INT NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_storage_policy (
  id BIGINT PRIMARY KEY,
  policy_code VARCHAR(64) NOT NULL UNIQUE,
  hot_threshold INT NOT NULL,
  warm_threshold INT NOT NULL,
  cool_target VARCHAR(32) NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_data_asset (
  id BIGINT PRIMARY KEY,
  asset_code VARCHAR(64) NOT NULL UNIQUE,
  fields_json CLOB NOT NULL,
  tags_json CLOB,
  storage_tier VARCHAR(16),
  lifecycle_status VARCHAR(16),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_marketplace_data (
  id BIGINT PRIMARY KEY,
  asset_code VARCHAR(64) NOT NULL UNIQUE,
  fields_json CLOB NOT NULL,
  tags_json CLOB,
  source VARCHAR(128),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE t_lifecycle_record (
  id BIGINT PRIMARY KEY,
  asset_code VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  operated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
