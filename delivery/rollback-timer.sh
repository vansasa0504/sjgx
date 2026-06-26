#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
DEPLOYMENT="${DEPLOYMENT:-platform-gateway}"
TIMEOUT="${TIMEOUT:-600s}"

start_ts="$(date +%s)"
echo "Start rollback for deployment/${DEPLOYMENT} in namespace ${NS}"
kubectl -n "${NS}" rollout undo deployment/"${DEPLOYMENT}"
kubectl -n "${NS}" rollout status deployment/"${DEPLOYMENT}" --timeout="${TIMEOUT}"
kubectl -n "${NS}" wait pod -l app="${DEPLOYMENT}" --for=condition=Ready --timeout="${TIMEOUT}" || true
end_ts="$(date +%s)"

elapsed=$(( end_ts - start_ts ))
echo "ROLLBACK_SECONDS=${elapsed}"
if [ "${elapsed}" -le 600 ]; then
  echo "ROLLBACK_TARGET=PASS"
else
  echo "ROLLBACK_TARGET=FAIL"
fi
