#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
OUT_DIR="${OUT_DIR:-perf/results}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-30}"
DURATION_SECONDS="${DURATION_SECONDS:-172800}"
METRICS="${METRICS:-jvm.memory.used,jvm.gc.pause,http.server.requests,hikaricp.connections.active}"

mkdir -p "${OUT_DIR}"
OUT_FILE="${OUT_FILE:-${OUT_DIR}/m6-metrics.csv}"

if [ ! -f "${OUT_FILE}" ]; then
  echo "timestamp,metric,value,status" > "${OUT_FILE}"
fi

end_at=$(( $(date +%s) + DURATION_SECONDS ))
while [ "$(date +%s)" -lt "${end_at}" ]; do
  ts="$(date -Iseconds)"
  health="$(curl -fsS "${BASE_URL}/actuator/health" || true)"
  if echo "${health}" | grep -q '"status":"UP"'; then
    health_status="UP"
  else
    health_status="DOWN"
  fi

  IFS=',' read -ra metric_names <<< "${METRICS}"
  for metric in "${metric_names[@]}"; do
    value="$(curl -fsS "${BASE_URL}/actuator/metrics/${metric}" \
      | sed -n 's/.*"measurements":\[\{"statistic":"[^"]*","value":\([^}]*\)\}\].*/\1/p' || true)"
    echo "${ts},${metric},${value:-NA},${health_status}" >> "${OUT_FILE}"
  done

  sleep "${INTERVAL_SECONDS}"
done
