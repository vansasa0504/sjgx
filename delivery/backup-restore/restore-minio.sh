#!/usr/bin/env bash
set -euo pipefail

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://127.0.0.1:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET="${MINIO_BUCKET:-sjgx-cold-storage}"
BACKUP_SOURCE="${BACKUP_SOURCE:-./backups/minio/${MINIO_BUCKET}}"
MINIO_ALIAS="${MINIO_ALIAS:-sjgx-dst}"

if [[ ! -d "${BACKUP_SOURCE}" ]]; then
  echo "MinIO backup source not found: ${BACKUP_SOURCE}" >&2
  exit 66
fi

mc alias set "${MINIO_ALIAS}" "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
mc mb --ignore-existing "${MINIO_ALIAS}/${MINIO_BUCKET}"
mc version enable "${MINIO_ALIAS}/${MINIO_BUCKET}" || true
mc mirror --overwrite "${BACKUP_SOURCE}" "${MINIO_ALIAS}/${MINIO_BUCKET}"

printf 'MinIO bucket restored: %s -> %s/%s\n' "${BACKUP_SOURCE}" "${MINIO_ENDPOINT}" "${MINIO_BUCKET}"
