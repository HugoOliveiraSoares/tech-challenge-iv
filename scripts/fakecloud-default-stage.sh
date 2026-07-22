#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TERRAFORM_DIR="$ROOT_DIR/infra/environments/dev"

AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
AWS_REGION="${AWS_REGION:-us-east-1}"

api_base_url="$(terraform -chdir="$TERRAFORM_DIR" output -raw api_base_url)"
api_host="${api_base_url#https://}"
api_host="${api_host#http://}"
api_host="${api_host%%/*}"
api_id="${api_host%%.*}"

existing_stage="$(AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}" \
  AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}" \
  aws --endpoint-url="$AWS_ENDPOINT_URL" --region "$AWS_REGION" \
    apigatewayv2 get-stages --api-id "$api_id" \
    --query "Items[?StageName=='\$default'].StageName | [0]" \
    --output text)"

if [[ "$existing_stage" == '$default' ]]; then
  exit 0
fi

AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}" \
  AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}" \
  aws --endpoint-url="$AWS_ENDPOINT_URL" --region "$AWS_REGION" \
    apigatewayv2 create-stage --api-id "$api_id" --stage-name '$default' --auto-deploy >/dev/null
