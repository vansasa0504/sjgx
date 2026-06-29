#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
OUT_DIR="${OUT_DIR:-target/e2e-p0}"
RUN_ID="$(date +%s)"
TOKEN=""

mkdir -p "$OUT_DIR"

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Missing required command: $command_name" >&2
    exit 127
  fi
}

require_command curl
require_command jq
require_command openssl
require_command sed

json_get() {
  local json="$1"
  local path="$2"
  printf '%s' "$json" | jq -r ".$path // \"\""
}

sign() {
  local api_key="$1"
  local secret="$2"
  local ts="$3"
  local nonce="$4"
  local body="$5"
  printf '%s\n%s\n%s\n%s' "$api_key" "$ts" "$nonce" "$body" \
    | openssl dgst -sha256 -hmac "$secret" -hex \
    | sed 's/^.*= //'
}

save() {
  local name="$1"
  local body="$2"
  printf '%s\n' "$body" > "$OUT_DIR/$name.json"
}

request() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local auth_header=()
  if [[ -n "$TOKEN" ]]; then
    auth_header=(-H "Authorization: Bearer $TOKEN")
  fi
  local response
  if [[ -n "$body" ]]; then
    response="$(curl -sS -X "$method" "$BASE_URL$path" "${auth_header[@]}" -H "Content-Type: application/json" -d "$body")"
  else
    response="$(curl -sS -X "$method" "$BASE_URL$path" "${auth_header[@]}")"
  fi
  save "$name" "$response"
  printf '%s' "$response"
}

echo "P0 E2E real-deps smoke started: BASE_URL=$BASE_URL OUT_DIR=$OUT_DIR"

login_body="{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASSWORD\"}"
login_response="$(request "01-login" POST "/auth/login" "$login_body")"
TOKEN="$(json_get "$login_response" "data.token")"
echo "1-LOGIN: token=***${TOKEN: -6}"
request "01-permissions" GET "/auth/permissions" >/dev/null
echo "1-PERMISSIONS: ok"

partner_response="$(request "02-partner-create" POST "/api/v1/partners" "{\"name\":\"p0-partner-$RUN_ID\",\"dataType\":\"CREDIT\",\"industry\":\"FINANCE\",\"complianceLevel\":\"L2\"}")"
partner_id="$(json_get "$partner_response" "data.id")"
request "02-partner-interface" POST "/api/v1/partners/$partner_id/interfaces" '{"protocol":"HTTP","endpoint":"http://localhost:8083/actuator/health","credential":"masked"}' >/dev/null
request "02-partner-submit" POST "/api/v1/partners/$partner_id/submit" >/dev/null
request "02-partner-approve" POST "/api/v1/partners/$partner_id/approve" >/dev/null
request "02-partner-admit" POST "/api/v1/partners/$partner_id/admit" >/dev/null
echo "2-PARTNER: id=$partner_id lifecycle=ADMITTED"

ingest_response="$(request "03-ingest-create" POST "/api/v1/ingest/tasks" "{\"partnerId\":$partner_id,\"endpoint\":\"http://localhost:8083/actuator/health\",\"syncMode\":\"FULL\",\"fieldMapping\":{},\"qualityRules\":[]}")"
ingest_id="$(json_get "$ingest_response" "data.id")"
request "03-ingest-test" POST "/api/v1/ingest/tasks/$ingest_id/test" >/dev/null || true
request "03-ingest-records" GET "/api/v1/ingest/tasks/records?taskId=$ingest_id" >/dev/null
request "03-ingest-submit" POST "/api/v1/ingest/tasks/$ingest_id/submit" >/dev/null || true
request "03-ingest-approve" POST "/api/v1/ingest/tasks/$ingest_id/approve" >/dev/null || true
echo "3-INGEST: id=$ingest_id"

service_code="p0-svc-$RUN_ID"
request "04-service-register" POST "/api/v1/services" "{\"serviceCode\":\"$service_code\",\"name\":\"P0 service\",\"routeKey\":\"route-$RUN_ID\"}" >/dev/null
request "04-service-define" POST "/api/v1/services/$service_code/define" >/dev/null
request "04-service-test" POST "/api/v1/services/$service_code/test" >/dev/null
request "04-service-publish" POST "/api/v1/services/$service_code/publish" >/dev/null
credential_response="$(request "04-service-credential" POST "/api/v1/services/$service_code/credentials" '{"consumerCode":"consumer-real"}')"
api_key="$(json_get "$credential_response" "data.apiKey")"
secret="$(json_get "$credential_response" "data.secret")"
params='{"amount":1}'
timestamp="$(date +%s)"
nonce="nonce-$RUN_ID"
signature="$(sign "$api_key" "$secret" "$timestamp" "$nonce" "$params")"
invoke_body="{\"consumerCode\":\"consumer-real\",\"apiKey\":\"$api_key\",\"timestamp\":$timestamp,\"nonce\":\"$nonce\",\"params\":\"$params\",\"signature\":\"$signature\"}"
curl -sS -X POST "$BASE_URL/api/v1/services/$service_code/invoke" -H "Content-Type: application/json" -H "X-Trace-Id: trace-p0-$RUN_ID" -d "$invoke_body" > "$OUT_DIR/04-service-invoke.json"
request "04-service-logs" GET "/api/v1/services/$service_code/logs" >/dev/null
echo "4-SERVICE: code=$service_code apiKey=***${api_key: -6} secret=***"

consumer_response="$(request "05-consumer-create" POST "/api/v1/consumers" '{"code":"consumer-real","name":"真实依赖消费方","bizLine":"risk","systemType":"CORE","complianceLevel":"L2"}')"
consumer_id="$(json_get "$consumer_response" "data.id")"
request "05-consumer-submit" POST "/api/v1/consumers/$consumer_id/events" '{"event":"SUBMIT"}' >/dev/null || true
request "05-consumer-approve" POST "/api/v1/consumers/$consumer_id/events" '{"event":"APPROVE"}' >/dev/null || true
request "05-consumer-quota" PUT "/api/v1/consumers/$consumer_id/quota" '{"dailyLimit":1000,"monthlyLimit":30000,"serviceScope":"ALL"}' >/dev/null
request "05-consumer-audit" GET "/api/v1/consumers/$consumer_id/audit" >/dev/null
request "05-consumer-logs" GET "/api/v1/consumers/$consumer_id/logs" >/dev/null
echo "5-CONSUMER: id=$consumer_id"

quality_rule="$(request "06-quality-rule" POST "/api/v1/quality/rules" '{"ruleCode":"P0_REQUIRED","dimension":"COMPLETENESS","field":"id","expression":{"required":true},"weight":10}')"
rule_id="$(json_get "$quality_rule" "data.id")"
request "06-quality-check" POST "/api/v1/quality/checks" "{\"batchNo\":\"B-$RUN_ID\",\"ruleIds\":[$rule_id],\"rows\":[{\"id\":\"1\"}],\"failRateThreshold\":1}" >/dev/null
request "06-quality-issues" GET "/api/v1/quality/issues" >/dev/null
request "06-quality-reports" GET "/api/v1/quality/reports" >/dev/null
echo "6-QUALITY: ruleId=$rule_id"

request "07-billing-rule" POST "/api/v1/billing/rules" '{"ruleCode":"P0_UNIT","ruleName":"P0 unit price","billingModel":"PER_CALL","targetType":"CONSUMER","targetId":1,"unitPrice":1.0000,"currency":"CNY","packageAllowance":0}' >/dev/null
bill_response="$(request "07-billing-generate" POST "/api/v1/billing/bills/generate" '{"billType":"EXPENSE","period":"MONTHLY"}')"
bill_no="$(json_get "$bill_response" "data.billNo")"
request "07-billing-confirm" POST "/api/v1/billing/bills/$bill_no/confirm" >/dev/null || true
request "07-billing-stats" GET "/api/v1/billing/stats" >/dev/null
echo "7-BILLING: billNo=$bill_no"

request "08-stats-dashboard" GET "/api/v1/stats/dashboard" >/dev/null
request "08-stats-audit" GET "/api/v1/stats/audit?traceId=trace-p0-$RUN_ID" >/dev/null
request "08-stats-audit-verify" GET "/api/v1/stats/audit/verify" >/dev/null
echo "8-STATS: trace=trace-p0-$RUN_ID auditVerify=ok"

request "09-users" GET "/users" >/dev/null
request "09-roles" GET "/roles" >/dev/null
request "09-permissions" GET "/permissions" >/dev/null
echo "9-SYSTEM: ok"

low_user="p0-viewer-$RUN_ID"
request "10-low-user-create" POST "/users" "{\"username\":\"$low_user\",\"password\":\"viewer123\",\"permissions\":[\"stats:view\"]}" >/dev/null
low_login="$(TOKEN="" request "10-low-login" POST "/auth/login" "{\"username\":\"$low_user\",\"password\":\"viewer123\"}")"
low_token="$(json_get "$low_login" "data.token")"
forbidden_code="$(curl -sS -o "$OUT_DIR/10-low-partner-forbidden.json" -w "%{http_code}" -H "Authorization: Bearer $low_token" "$BASE_URL/api/v1/partners")"
stats_code="$(curl -sS -o "$OUT_DIR/10-low-stats-ok.json" -w "%{http_code}" -H "Authorization: Bearer $low_token" "$BASE_URL/api/v1/stats/dashboard")"
test "$forbidden_code" = "403"
test "$stats_code" = "200"
echo "10-PERMISSION: partners=$forbidden_code stats=$stats_code"

echo "P0 E2E real-deps smoke completed. Evidence: $OUT_DIR"
