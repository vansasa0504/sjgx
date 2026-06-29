CREATE TABLE IF NOT EXISTS t_catalog_application (
    id BIGINT PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    applicant VARCHAR(128) NOT NULL,
    reason VARCHAR(512),
    scope VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    approver VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    approved_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_catalog_application_catalog ON t_catalog_application (catalog_id);
CREATE INDEX IF NOT EXISTS idx_catalog_application_applicant ON t_catalog_application (applicant);
CREATE INDEX IF NOT EXISTS idx_catalog_application_status ON t_catalog_application (status);
