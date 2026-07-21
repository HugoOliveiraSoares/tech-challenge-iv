#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AWS_REGION="${AWS_REGION:-us-east-1}"
ADMIN_EMAIL_TO="${ADMIN_EMAIL_TO:-admin@example.com}"
EMAIL_FROM="${EMAIL_FROM:-no-reply@example.com}"

terraform -chdir="$ROOT_DIR/infra/environments/dev" init
terraform -chdir="$ROOT_DIR/infra/environments/dev" apply -auto-approve \
  -var="aws_region=$AWS_REGION" \
  -var="admin_email_to=$ADMIN_EMAIL_TO" \
  -var="email_from=$EMAIL_FROM"

terraform -chdir="$ROOT_DIR/infra/environments/dev" output
