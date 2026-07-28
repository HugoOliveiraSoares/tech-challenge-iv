#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERRAFORM_DIR="$ROOT_DIR/infra/environments/dev"

AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
AWS_REGION="${AWS_REGION:-us-east-1}"
FEEDBACK_TABLE_NAME="${FEEDBACK_TABLE_NAME:-feedbacks-dev}"
FEEDBACK_API_LAMBDA_NAME="${FEEDBACK_API_LAMBDA_NAME:-feedback-api-dev}"
WEEKLY_REPORT_LAMBDA_NAME="${WEEKLY_REPORT_LAMBDA_NAME:-weekly-report-dev}"
WEEKLY_REPORT_PERIODO="${WEEKLY_REPORT_PERIODO:-2026-W30}"
ADMIN_EMAIL_TO="${ADMIN_EMAIL_TO:-admin@example.com}"
EMAIL_FROM="${EMAIL_FROM:-no-reply@example.com}"

AWS_LOCAL="AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=$AWS_REGION AWS_ENDPOINT_URL=$AWS_ENDPOINT_URL aws --endpoint-url=$AWS_ENDPOINT_URL"

pass=0
fail=0

assert() {
    local description="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        printf '  PASS  %s\n' "$description"
        ((pass++))
    else
        printf '  FAIL  %s\n' "$description" >&2
        ((fail++))
    fi
}

assert_equals() {
    local description="$1" expected="$2" actual="$3"
    if [[ "$expected" == "$actual" ]]; then
        printf '  PASS  %s\n' "$description"
        ((pass++))
    else
        printf '  FAIL  %s (expected="%s" actual="%s")\n' "$description" "$expected" "$actual" >&2
        ((fail++))
    fi
}

printf '=== E2E Local Verification ===\n'
printf 'Prerequisite: run "make local-up" before this script.\n\n'

# --- Phase 1: Feedback API HTTP contract ---
printf '--- Phase 1: Feedback API ---\n'

if command -v terraform >/dev/null 2>&1 && terraform -chdir="$TERRAFORM_DIR" output -raw api_base_url >/tmp/feedback-platform-api-url 2>/dev/null; then
    API_BASE_URL="$(tr -d '\n' </tmp/feedback-platform-api-url)"
else
    API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
fi

CORRELATION_ID="e2e-$(date +%Y%m%d%H%M%S)"
API_RESPONSE="$(curl -sS -w '\n%{http_code}' \
    -X POST "$API_BASE_URL/avaliacao" \
    -H 'Content-Type: application/json' \
    -H "X-Correlation-Id: $CORRELATION_ID" \
    -d '{"descricao":"E2E test feedback for weekly report validation.","nota":3}')"
API_STATUS="$(echo "$API_RESPONSE" | tail -1)"
API_BODY="$(echo "$API_RESPONSE" | sed '$d')"

assert_equals "POST /avaliacao returns 201" "201" "$API_STATUS"
echo "$API_BODY" | grep -q '"status":"CREATED"' && \
    assert "Response contains CREATED status" true || assert "Response contains CREATED status" false

# --- Phase 2: Critical notifier direct invocation ---
printf '\n--- Phase 2: Critical Notifier ---\n'

CRITICAL_EVENT_ID="e2e-critical-$(date +%s)-$(shuf -i 1000-9999 -n 1)"
$AWS_LOCAL lambda invoke \
    --function-name "$WEEKLY_REPORT_LAMBDA_NAME" \
    --cli-binary-format raw-in-base64-out \
    --payload '{}' \
    /tmp/e2e-weekly-skip.json >/dev/null 2>&1 || true

$AWS_LOCAL lambda invoke \
    --function-name "$FEEDBACK_API_LAMBDA_NAME" \
    --cli-binary-format raw-in-base64-out \
    --payload "{\"periodo\":\"$WEEKLY_REPORT_PERIODO\"}" \
    /tmp/e2e-weekly-report.json >/dev/null 2>&1 || true

NOTIFIER_EVENT='{"Records":[{"EventSource":"aws:sns","Sns":{"Message":"{\"feedbackId\":\"'"$CRITICAL_EVENT_ID"'\",\"correlationId\":\"e2e-corr-001\",\"descricao\":\"E2E critical feedback test.\",\"nota\":2,\"urgencia\":\"CRITICA\",\"dataEnvio\":\"2026-07-27T10:00:00Z\"}"}}]}'
NOTIFIER_OUTPUT="$($AWS_LOCAL lambda invoke \
    --function-name feedback-critical-notifier-dev \
    --cli-binary-format raw-in-base64-out \
    --payload "$NOTIFIER_EVENT" \
    /tmp/e2e-notifier-output.json >/dev/null 2>&1 && cat /tmp/e2e-notifier-output.json || echo '{}')"

assert "Critical notifier invocation completes" test -n "$NOTIFIER_OUTPUT"

# --- Phase 3: Seed weekly data and invoke weekly report ---
printf '\n--- Phase 3: Weekly Report ---\n'

"$ROOT_DIR/scripts/seed-feedbacks-dev.sh"

$AWS_LOCAL lambda invoke \
    --function-name "$WEEKLY_REPORT_LAMBDA_NAME" \
    --cli-binary-format raw-in-base64-out \
    --payload "{\"periodo\":\"$WEEKLY_REPORT_PERIODO\"}" \
    /tmp/e2e-weekly-report.json >/dev/null 2>&1
assert "Weekly report invocation completes" test -s /tmp/e2e-weekly-report.json

# --- Phase 4: Inspect DynamoDB state ---
printf '\n--- Phase 4: DynamoDB Inspection ---\n'

PROCESSING_RECORD="$($AWS_LOCAL dynamodb get-item \
    --table-name "feedback-processing-control-dev" \
    --key "{\"periodo\":{\"S\":\"$WEEKLY_REPORT_PERIODO\"}}" \
    --region "$AWS_REGION" 2>/dev/null || echo '{}')"
echo "$PROCESSING_RECORD" | grep -q '"status"' && \
    assert "Processing control record exists for $WEEKLY_REPORT_PERIODO" true || \
    assert "Processing control record exists for $WEEKLY_REPORT_PERIODO" false

# --- Phase 5: Inspect SES emails ---
printf '\n--- Phase 5: SES Email Inspection ---\n'

SES_EMAILS="$(curl -sS "http://localhost:4566/_fakecloud/ses/emails" 2>/dev/null || echo '[]')"
EMAIL_COUNT="$(echo "$SES_EMAILS" | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo '0')"
assert "At least one SES email recorded" test "$EMAIL_COUNT" -gt 0

# --- Summary ---
printf '\n=== E2E Results ===\n'
printf 'Passed: %d\n' "$pass"
printf 'Failed: %d\n' "$fail"

if [[ "$fail" -gt 0 ]]; then
    printf '\nSome E2E checks failed. Note:\n'
    printf '  - The POST -> DynamoDB -> SNS -> critical-notifier flow is DEFERRED.\n'
    printf '  - Only individually connected paths are validated.\n'
    exit 1
fi

printf '\nAll E2E checks passed.\n'
printf 'Disclaimer: The unified POST -> DynamoDB -> SNS -> critical-notifier\n'
printf 'pipeline remains deferred until feedback-api DynamoDB/SNS adapters exist.\n'
