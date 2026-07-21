#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERRAFORM_DIR="$ROOT_DIR/infra/environments/dev"

if command -v terraform >/dev/null 2>&1 && terraform -chdir="$TERRAFORM_DIR" output -raw api_base_url >/tmp/feedback-platform-api-url 2>/dev/null; then
  API_BASE_URL="$(tr -d '\n' </tmp/feedback-platform-api-url)"
else
  API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
fi

CORRELATION_ID="local-smoke-$(date +%Y%m%d%H%M%S)"
PAYLOAD='{"descricao":"A aula estava confusa e nao consegui acompanhar o conteudo.","nota":2}'

response_file="$(mktemp)"
status_code="$(curl -sS -o "$response_file" -w '%{http_code}' \
  -X POST "$API_BASE_URL/avaliacao" \
  -H 'Content-Type: application/json' \
  -H "X-Correlation-Id: $CORRELATION_ID" \
  -d "$PAYLOAD")"

if [[ "$status_code" != "201" ]]; then
  printf 'Smoke test failed. Expected HTTP 201, got %s. Response:\n' "$status_code" >&2
  sed 's/^/  /' "$response_file" >&2
  exit 1
fi

printf 'Smoke test passed against %s with correlation id %s.\n' "$API_BASE_URL" "$CORRELATION_ID"
