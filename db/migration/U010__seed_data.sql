-- U010: Seed data for user/role/apikey/catalog

-- Admin user (password: admin123, BCrypt hash)
INSERT INTO t_user (id, username, password_hash) VALUES
    (1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjdQRJZQbE8Pmm1iZvYJFOaVZCJqVy')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);

-- Admin permissions
INSERT INTO t_user_permission (user_id, permission_code) VALUES
    (1, 'partner:view'), (1, 'partner:create'), (1, 'partner:update'), (1, 'partner:approve'),
    (1, 'consumer:view'), (1, 'consumer:create'), (1, 'consumer:update'), (1, 'consumer:approve'),
    (1, 'ingest:view'), (1, 'ingest:create'), (1, 'ingest:update'), (1, 'ingest:approve'),
    (1, 'service:view'), (1, 'service:create'), (1, 'service:update'), (1, 'service:approve'),
    (1, 'catalog:view'), (1, 'catalog:apply'), (1, 'catalog:approve'),
    (1, 'quality:view'), (1, 'quality:create'), (1, 'quality:update'), (1, 'quality:run'),
    (1, 'billing:view'), (1, 'billing:create'), (1, 'billing:update'), (1, 'billing:approve'), (1, 'billing:run'),
    (1, 'stats:view'),
    (1, 'system:view'), (1, 'system:create'), (1, 'system:update')
ON DUPLICATE KEY UPDATE permission_code = permission_code;

-- Demo API key for e2e testing
INSERT INTO t_api_credential (api_key, secret, consumer_code, service_code, enabled) VALUES
    ('api-key', 'secret', 'e2e-consumer', NULL, 1)
ON DUPLICATE KEY UPDATE secret = VALUES(secret);

-- Catalog seed data (moved from CatalogService production code)
INSERT INTO t_data_catalog_item (id, catalog_code, name, subject, partner_id, data_type, scenario, field_definitions, format, update_frequency, source, compliance_note, usage_limit) VALUES
    (1, 'CATALOG-DEMO', '示例外部数据资产', '征信主题', 1, 'JSON', '风控', 'name,score', 'JSON', 'DAILY', 'DEMO', '测试环境示例资产', '仅用于开发回归')
ON DUPLICATE KEY UPDATE name = VALUES(name);