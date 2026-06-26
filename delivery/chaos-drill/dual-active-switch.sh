#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
PRIMARY="${PRIMARY:-platform-a}"
SECONDARY="${SECONDARY:-platform-b}"
RPO_MARK_FILE="${RPO_MARK_FILE:-/tmp/sjgx-rpo-marker.txt}"

start_ts="$(date +%s)"
date -Iseconds > "${RPO_MARK_FILE}"
echo "RPO marker written to ${RPO_MARK_FILE}: $(cat "${RPO_MARK_FILE}")"

echo "Switch traffic by scaling primary ${PRIMARY} to 0 and ensuring ${SECONDARY} is ready."
kubectl -n "${NS}" scale deployment/"${PRIMARY}" --replicas=0
kubectl -n "${NS}" rollout status deployment/"${SECONDARY}" --timeout=300s
kubectl -n "${NS}" get endpoints

echo "Recover primary as standby."
kubectl -n "${NS}" scale deployment/"${PRIMARY}" --replicas="${PRIMARY_REPLICAS:-1}"
kubectl -n "${NS}" rollout status deployment/"${PRIMARY}" --timeout=300s
end_ts="$(date +%s)"

echo "RTO_SECONDS=$(( end_ts - start_ts ))"
echo "Expected: RPO <=5min and RTO <=30min. Compare marker with last replicated data timestamp."
