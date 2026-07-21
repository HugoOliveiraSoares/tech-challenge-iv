#!/usr/bin/env bash
set -euo pipefail

HOST="${FAKECLOUD_HOST:-127.0.0.1}"
PORT="${FAKECLOUD_PORT:-4566}"
TIMEOUT_SECONDS="${FAKECLOUD_WAIT_TIMEOUT:-60}"

deadline=$((SECONDS + TIMEOUT_SECONDS))

while [ "$SECONDS" -lt "$deadline" ]; do
  if (exec 3<>"/dev/tcp/$HOST/$PORT") 2>/dev/null; then
    exec 3>&-
    exec 3<&-
    printf 'fakecloud disponivel em %s:%s\n' "$HOST" "$PORT"
    exit 0
  fi

  sleep 2
done

printf 'fakecloud nao ficou disponivel em %s:%s dentro de %ss\n' "$HOST" "$PORT" "$TIMEOUT_SECONDS" >&2
exit 1
