-- Rollback V010__user_role_apikey.sql.

DROP TABLE t_api_credential;
DROP TABLE t_user_role;
DROP TABLE t_role_permission;
DROP TABLE t_user_permission;

ALTER TABLE t_role DROP COLUMN created_at;
ALTER TABLE t_user DROP COLUMN updated_at;
