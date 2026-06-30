#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
DEPLOYMENT="${DEPLOYMENT:-platform-a}"
IMAGE="${IMAGE:-}"
HEALTH_URL="${HEALTH_URL:-http://gateway.sjgx.local/actuator/health}"
TIMEOUT="${TIMEOUT:-600s}"

start_ts="$(date +%s)"
echo "Start rolling upgrade drill for deployment/${DEPLOYMENT} in namespace ${NS}."

if [ -n "${IMAGE}" ]; then
  echo "Apply canary image ${IMAGE}."
  kubectl -n "${NS}" set image deployment/"${DEPLOYMENT}" "*=${IMAGE}"
else
  echo "No IMAGE provided; restart deployment to simulate rolling upgrade."
  kubectl -n "${NS}" rollout restart deployment/"${DEPLOYMENT}"
fi

kubectl -n "${NS}" rollout status deployment/"${DEPLOYMENT}" --timeout="${TIMEOUT}"
curl -fsS "${HEALTH_URL}" >/dev/null
upgrade_ready_ts="$(date +%s)"
echo "UPGRADE_READY_SECONDS=$(( upgrade_ready_ts - start_ts ))"

echo "Simulate rollback."
rollback_start_ts="$(date +%s)"
kubectl -n "${NS}" rollout undo deployment/"${DEPLOYMENT}"
kubectl -n "${NS}" rollout status deployment/"${DEPLOYMENT}" --timeout="${TIMEOUT}"
curl -fsS "${HEALTH_URL}" >/dev/null
rollback_end_ts="$(date +%s)"

echo "ROLLBACK_SECONDS=$(( rollback_end_ts - rollback_start_ts ))"
echo "Expected: rolling upgrade has no user-visible outage; rollback <=600s. Fill real values after production K8s drill."
