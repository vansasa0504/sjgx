#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
SERVICE="${SERVICE:-redis}"
REPLICAS_BEFORE="${REPLICAS_BEFORE:-1}"
HEALTH_URL="${HEALTH_URL:-http://gateway.sjgx.local/actuator/health}"

start_ts="$(date +%s)"
echo "Inject Redis outage: ${SERVICE}"
kubectl -n "${NS}" scale deployment/"${SERVICE}" --replicas=0

echo "Verify JWT stateless auth and quota local fallback path."
curl -fsS "${HEALTH_URL}" || true
kubectl -n "${NS}" logs -l app=platform-gateway --tail=100 || true
kubectl -n "${NS}" logs -l app=platform-partner --tail=200 | grep -E "Redis quota counter unavailable|falling back to local quota counter" || true

echo "Recover Redis."
kubectl -n "${NS}" scale deployment/"${SERVICE}" --replicas="${REPLICAS_BEFORE}"
kubectl -n "${NS}" rollout status deployment/"${SERVICE}" --timeout=300s
end_ts="$(date +%s)"

echo "RTO_SECONDS=$(( end_ts - start_ts ))"
echo "Expected: cache degradation only; quota requests use local fallback during outage, then Redis counter resumes after recovery."
