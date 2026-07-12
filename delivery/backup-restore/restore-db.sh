#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-sjgx}"
DB_USER="${DB_USER:-sjgx}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_FILE="${BACKUP_FILE:-${1:-}}"
AUDIT_VERIFY_URL="${AUDIT_VERIFY_URL:-}"
AUTH_HEADER="${AUTH_HEADER:-}"

if [[ -z "${BACKUP_FILE}" ]]; then
  echo "BACKUP_FILE or first positional argument is required" >&2
  exit 64
fi
if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "Backup file not found: ${BACKUP_FILE}" >&2
  exit 66
fi
if [[ ! "${DB_NAME}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "DB_NAME must be a valid unquoted database identifier" >&2
  exit 64
fi

TABLES=(
  t_service_invoke_log
  t_service_invoke_log_archive
  t_audit_log
  t_audit_log_archive
  t_bill
  t_bill_item
  t_finance_sync_record
  t_raw_data
  t_data_catalog
  t_user
  t_api_credential
  t_lifecycle_record
)

export MYSQL_PWD="${DB_PASSWORD}"
trap 'unset MYSQL_PWD' EXIT

table_names="$(IFS="','"; echo "${TABLES[*]}")"
existing_table_count="$(mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --batch \
  --skip-column-names \
  --execute="SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '${DB_NAME}' AND table_name IN ('${table_names}');")"
if [[ "${existing_table_count}" != "0" ]]; then
  echo "Restore target ${DB_NAME} contains ${existing_table_count} managed table(s); use a new isolated database" >&2
  exit 65
fi

mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" "${DB_NAME}" < "${BACKUP_FILE}"

for table in "${TABLES[@]}"; do
  mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_USER}" --batch --skip-column-names \
    --execute="SELECT '${table}', COUNT(*) FROM ${DB_NAME}.${table};"
done
if [[ -n "${AUDIT_VERIFY_URL}" ]]; then
  if [[ -n "${AUTH_HEADER}" ]]; then
    curl -fsS -H "${AUTH_HEADER}" "${AUDIT_VERIFY_URL}"
  else
    curl -fsS "${AUDIT_VERIFY_URL}"
  fi
else
  printf 'Audit verify endpoint not called. Set AUDIT_VERIFY_URL=/api/v1/stats/audit/verify after service restore.\n'
fi

printf 'DB restore completed from: %s\n' "${BACKUP_FILE}"
