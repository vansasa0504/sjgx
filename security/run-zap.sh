#!/usr/bin/env bash
set -euo pipefail

TARGET_URL="${ZAP_TARGET_URL:-http://localhost:8080}"
REPORT_DIR="${ZAP_REPORT_DIR:-security/reports}"
REPORT_BASENAME="${ZAP_REPORT_BASENAME:-zap-p2-04}"
AUTH_TOKEN="${ZAP_AUTH_TOKEN:-}"

mkdir -p "${REPORT_DIR}"

ZAP_ARGS=(
  zap-baseline.py
  -t "${TARGET_URL}"
  -r "${REPORT_BASENAME}.html"
  -J "${REPORT_BASENAME}.json"
)

if [[ -n "${AUTH_TOKEN}" ]]; then
  ZAP_ARGS+=(
    -z "auth.bearer=${AUTH_TOKEN}"
  )
fi

docker run --rm -t \
  -v "$(pwd)/${REPORT_DIR}:/zap/wrk" \
  ghcr.io/zaproxy/zaproxy:stable \
  "${ZAP_ARGS[@]}"
