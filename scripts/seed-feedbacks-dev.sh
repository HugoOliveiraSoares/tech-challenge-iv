#!/usr/bin/env bash
set -euo pipefail

AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:4566}"
AWS_REGION="${AWS_REGION:-us-east-1}"
FEEDBACK_TABLE_NAME="${FEEDBACK_TABLE_NAME:-feedbacks-dev}"
SEED_FEEDBACK_PERIODO="${SEED_FEEDBACK_PERIODO:-2026-W30}"

aws --endpoint-url="${AWS_ENDPOINT_URL}" dynamodb batch-write-item \
  --request-items "{
    \"${FEEDBACK_TABLE_NAME}\": [
      {
        \"PutRequest\": {
          \"Item\": {
            \"id\": {\"S\": \"11111111-1111-1111-1111-111111111111\"},
            \"periodo\": {\"S\": \"${SEED_FEEDBACK_PERIODO}\"},
            \"dataEnvio\": {\"S\": \"2026-07-20T10:00:00Z\"},
            \"descricao\": {\"S\": \"Atendimento excelente e conteudo explicado com clareza.\"},
            \"nota\": {\"N\": \"9\"},
            \"urgencia\": {\"S\": \"BAIXA\"}
          }
        }
      },
      {
        \"PutRequest\": {
          \"Item\": {
            \"id\": {\"S\": \"22222222-2222-2222-2222-222222222222\"},
            \"periodo\": {\"S\": \"${SEED_FEEDBACK_PERIODO}\"},
            \"dataEnvio\": {\"S\": \"2026-07-21T12:00:00Z\"},
            \"descricao\": {\"S\": \"Problema critico no pedido precisa de atendimento imediato.\"},
            \"nota\": {\"N\": \"2\"},
            \"urgencia\": {\"S\": \"CRITICA\"}
          }
        }
      },
      {
        \"PutRequest\": {
          \"Item\": {
            \"id\": {\"S\": \"33333333-3333-3333-3333-333333333333\"},
            \"periodo\": {\"S\": \"${SEED_FEEDBACK_PERIODO}\"},
            \"dataEnvio\": {\"S\": \"2026-07-22T15:30:00Z\"},
            \"descricao\": {\"S\": \"Experiencia regular com alguns pontos de melhoria.\"},
            \"nota\": {\"N\": \"5\"},
            \"urgencia\": {\"S\": \"MEDIA\"}
          }
        }
      }
    ]
  }" \
  --region="${AWS_REGION}"

printf 'Seeded %s with periodo=%s\n' "${FEEDBACK_TABLE_NAME}" "${SEED_FEEDBACK_PERIODO}"
