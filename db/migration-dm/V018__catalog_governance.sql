CREATE TABLE t_catalog_lineage (
    id BIGINT PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    node_id BIGINT NOT NULL,
    node_name VARCHAR(128),
    direction VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_catalog_lineage_node ON t_catalog_lineage(catalog_id, node_type, node_id, direction);
CREATE INDEX idx_lineage_catalog ON t_catalog_lineage(catalog_id);

CREATE TABLE t_catalog_quality_summary (
    id BIGINT PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    issue_count INT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_catalog_quality_summary_catalog ON t_catalog_quality_summary(catalog_id);
