#!/usr/bin/env bash
set -euo pipefail

cat <<'ENV'
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export FEEDBACK_TABLE_NAME=feedbacks-dev
export CRITICAL_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:feedback-critical-topic-dev
export ADMIN_EMAIL_TO=admin@example.com
export EMAIL_FROM=no-reply@example.com
ENV
