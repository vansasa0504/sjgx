#!/usr/bin/env bash
set -euo pipefail

NS=${NS:-sjgx-dev}
SERVICE=${SERVICE:-sjgx-platform}
START_TS=$(date +%s)

echo "[M5] Simulate site A outage"
kubectl -n "$NS" scale deployment/platform-a --replicas=0

echo "[M5] Wait for site B to be ready"
kubectl -n "$NS" rollout status deployment/platform-b --timeout=300s
kubectl -n "$NS" get endpoints "$SERVICE"

READY_TS=$(date +%s)
echo "RTO_SECONDS=$((READY_TS-START_TS))"
echo "RPO check: validate DB/Kafka replication lag externally;待 M6 外部环境实测"

echo "[M5] Restore site A"
kubectl -n "$NS" scale deployment/platform-a --replicas=${SITE_A_REPLICAS:-2}
kubectl -n "$NS" rollout status deployment/platform-a --timeout=300s
