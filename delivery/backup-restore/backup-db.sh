#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-sjgx}"
DB_USER="${DB_USER:-sjgx}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_FILE="${BACKUP_DIR}/backup-${TIMESTAMP}.sql"

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

mkdir -p "${BACKUP_DIR}"

export MYSQL_PWD="${DB_PASSWORD}"
trap 'unset MYSQL_PWD' EXIT
mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USER}" \
  --single-transaction \
  --routines \
  --no-tablespaces \
  "${DB_NAME}" "${TABLES[@]}" > "${OUTPUT_FILE}"
printf 'DB backup written: %s\n' "${OUTPUT_FILE}"
printf 'Production note: replace logical mysqldump with the institution database physical backup/snapshot plan when required.\n'
