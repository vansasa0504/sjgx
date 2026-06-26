#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
PRIMARY_LABEL="${PRIMARY_LABEL:-app=dm-primary}"
STANDBY_LABEL="${STANDBY_LABEL:-app=dm-standby}"
CHECK_SQL="${CHECK_SQL:-SELECT COUNT(*) FROM t_service_invoke_log;}"
DB_CLIENT_POD="${DB_CLIENT_POD:-}"

start_ts="$(date +%s)"
echo "Capture pre-failover count with SQL: ${CHECK_SQL}"
if [ -n "${DB_CLIENT_POD}" ]; then
  kubectl -n "${NS}" exec "${DB_CLIENT_POD}" -- sh -c "echo \"${CHECK_SQL}\" | db-client" || true
fi

echo "Inject primary DB outage by scaling primary workload to 0."
kubectl -n "${NS}" scale deployment -l "${PRIMARY_LABEL}" --replicas=0
kubectl -n "${NS}" wait pod -l "${STANDBY_LABEL}" --for=condition=Ready --timeout=300s

echo "Verify application health and post-failover count."
kubectl -n "${NS}" get pods -o wide
if [ -n "${DB_CLIENT_POD}" ]; then
  kubectl -n "${NS}" exec "${DB_CLIENT_POD}" -- sh -c "echo \"${CHECK_SQL}\" | db-client" || true
fi

end_ts="$(date +%s)"
echo "RTO_SECONDS=$(( end_ts - start_ts ))"
echo "Expected: RPO <=5min, RTO <=30min, count before/after equal unless accepted in-flight writes are documented."
