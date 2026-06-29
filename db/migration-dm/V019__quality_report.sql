CREATE TABLE t_quality_report (
    id BIGINT PRIMARY KEY,
    dimension VARCHAR(32) NOT NULL,
    dimension_value VARCHAR(128) NOT NULL,
    check_count INT NOT NULL,
    pass_count INT NOT NULL,
    fail_count INT NOT NULL,
    fail_rate DECIMAL(5,4) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    generated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_quality_report_dimension ON t_quality_report(dimension, generated_at);
