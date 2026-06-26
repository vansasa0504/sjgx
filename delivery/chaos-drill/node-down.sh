#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
SERVICE="${SERVICE:-platform-a}"
DEPLOYMENT="${DEPLOYMENT:-deployment/${SERVICE}}"
REPLICAS_BEFORE="${REPLICAS_BEFORE:-1}"

start_ts="$(date +%s)"
echo "Inject node/service outage: ${DEPLOYMENT} in ${NS}"
kubectl -n "${NS}" scale "${DEPLOYMENT}" --replicas=0
kubectl -n "${NS}" rollout status "${DEPLOYMENT}" --timeout=60s || true

echo "Check gateway health from remaining active deployment."
kubectl -n "${NS}" get pods -o wide
kubectl -n "${NS}" get endpoints

echo "Recover ${DEPLOYMENT}"
kubectl -n "${NS}" scale "${DEPLOYMENT}" --replicas="${REPLICAS_BEFORE}"
kubectl -n "${NS}" rollout status "${DEPLOYMENT}" --timeout=300s
end_ts="$(date +%s)"

echo "RTO_SECONDS=$(( end_ts - start_ts ))"
echo "Expected: single node failover <=30s, cluster recovery <=5min. Fill actual result in chaos-report-template.md."
