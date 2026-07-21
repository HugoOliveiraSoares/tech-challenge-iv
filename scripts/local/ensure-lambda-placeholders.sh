#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

ensure_placeholder() {
  local artifact_path="$1"

  if [ ! -f "$artifact_path" ]; then
    mkdir -p "$(dirname "$artifact_path")"
    printf 'placeholder for terraform validate\n' > "$artifact_path"
  fi
}

ensure_placeholder "$ROOT_DIR/apps/feedback-api/target/function.zip"
ensure_placeholder "$ROOT_DIR/apps/critical-notifier/target/function.zip"
ensure_placeholder "$ROOT_DIR/apps/weekly-report/target/function.zip"
