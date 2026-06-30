#!/usr/bin/env bash
set -euo pipefail

NS="${NS:-sjgx-dev}"
SERVICE="${SERVICE:-kafka}"
REPLICAS_BEFORE="${REPLICAS_BEFORE:-1}"
CONSUMER_DEPLOYMENT="${CONSUMER_DEPLOYMENT:-platform-billing}"
PIPELINE_DEPLOYMENT="${PIPELINE_DEPLOYMENT:-platform-pipeline}"

start_ts="$(date +%s)"
echo "Inject Kafka outage: ${SERVICE}"
kubectl -n "${NS}" scale deployment/"${SERVICE}" --replicas=0

echo "Check application degraded behavior: invoke logs should fall back to JDBC when Kafka send fails."
kubectl -n "${NS}" logs deployment/"${PIPELINE_DEPLOYMENT}" --tail=200 | grep -E "Kafka invoke-log write failed|falling back to JDBC" || true
echo "Check consumer lag alarms."
kubectl -n "${NS}" logs deployment/"${CONSUMER_DEPLOYMENT}" --tail=100 || true
kubectl -n "${NS}" get events --sort-by=.lastTimestamp | tail -50

echo "Recover Kafka and scale consumers if lag remains."
kubectl -n "${NS}" scale deployment/"${SERVICE}" --replicas="${REPLICAS_BEFORE}"
kubectl -n "${NS}" rollout status deployment/"${SERVICE}" --timeout=300s
kubectl -n "${NS}" scale deployment/"${CONSUMER_DEPLOYMENT}" --replicas="${CONSUMER_REPLICAS:-2}" || true
end_ts="$(date +%s)"

echo "RTO_SECONDS=$(( end_ts - start_ts ))"
echo "Expected: invoke logs are persisted through JDBC fallback during outage; backlog alarm is raised and drains after recovery."
