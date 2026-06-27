-- V010: User/Role/Permission tables + API key credential + catalog seed data

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 用户权限码表
CREATE TABLE IF NOT EXISTS t_user_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_user_perm (user_id, permission_code),
    CONSTRAINT fk_user_perm FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE
);

-- 角色表
CREATE TABLE IF NOT EXISTS t_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 角色权限码表
CREATE TABLE IF NOT EXISTS t_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, permission_code),
    CONSTRAINT fk_role_perm FOREIGN KEY (role_id) REFERENCES t_role(id) ON DELETE CASCADE
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS t_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES t_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES t_role(id) ON DELETE CASCADE
);

-- 数据服务 API Key 凭证表（apiKey -> secret 映射）
CREATE TABLE IF NOT EXISTS t_api_credential (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key VARCHAR(128) NOT NULL UNIQUE,
    secret VARCHAR(256) NOT NULL,
    consumer_code VARCHAR(64) NOT NULL,
    service_code VARCHAR(128),
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);