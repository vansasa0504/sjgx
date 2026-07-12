#!/usr/bin/env bash
set -euo pipefail

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://127.0.0.1:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
MINIO_BUCKET="${MINIO_BUCKET:-sjgx-cold-storage}"
BACKUP_TARGET="${BACKUP_TARGET:-./backups/minio/${MINIO_BUCKET}}"
MINIO_ALIAS="${MINIO_ALIAS:-sjgx-src}"

mc alias set "${MINIO_ALIAS}" "${MINIO_ENDPOINT}" "${MINIO_ACCESS_KEY}" "${MINIO_SECRET_KEY}"
mc version enable "${MINIO_ALIAS}/${MINIO_BUCKET}" || true
mc mirror --overwrite --remove "${MINIO_ALIAS}/${MINIO_BUCKET}" "${BACKUP_TARGET}"

printf 'MinIO bucket backup mirrored: %s/%s -> %s\n' "${MINIO_ENDPOINT}" "${MINIO_BUCKET}" "${BACKUP_TARGET}"
printf 'Production note: enable bucket versioning, lifecycle policy, and offsite replication on production MinIO/S3.\n'
