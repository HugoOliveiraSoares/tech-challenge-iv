# Ambiente de Desenvolvimento

Este guia descreve o fluxo local recomendado para desenvolver, testar e demonstrar o projeto sem depender de uma conta AWS real.

## Modos De Uso

Use o modo rapido para desenvolver a API HTTP e regras de negocio com Quarkus dev mode:

```bash
make dev
```

Use o modo integracao local para empacotar as Lambdas, subir fakecloud e provisionar a infraestrutura `dev` com Terraform:

```bash
make local-up
```

O modo integracao local provisiona os recursos modelados em `infra/environments/dev` usando os endpoints fake AWS em `http://localhost:4566`.

## Pre-Requisitos

- Java 21, preferencialmente via `mise install`.
- Docker e Docker Compose.
- Terraform 1.6+.
- Node/npm para validacao OpenAPI via `npx`.
- `curl` para o smoke test.

## Comandos Principais

Listar comandos disponiveis:

```bash
make help
```

Subir apenas o fakecloud:

```bash
make fakecloud-up
```

Rodar testes unitarios:

```bash
make test
```

Rodar testes de integracao quando houver classes `*IT.java`:

```bash
make test-it
```

Gerar os pacotes Lambda usados pelo Terraform:

```bash
make package
```

Validar infraestrutura local:

```bash
make terraform-dev-validate
```

Aplicar infraestrutura local no fakecloud:

```bash
make terraform-dev-apply
```

Executar uma chamada de smoke test:

```bash
make smoke
```

Destruir a infraestrutura local e parar o fakecloud:

```bash
make local-down
```

Rodar a verificacao principal local:

```bash
make verify
```

## Variaveis Locais

Para imprimir os exports usados no ambiente local:

```bash
make env
```

Para aplicar no shell atual:

```bash
eval "$(./scripts/local-env.sh)"
```

Valores padrao:

```bash
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_REGION=us-east-1
AWS_ENDPOINT_URL=http://localhost:4566
FEEDBACK_TABLE_NAME=feedbacks-dev
CRITICAL_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:feedback-critical-topic-dev
ADMIN_EMAIL_TO=admin@example.com
EMAIL_FROM=no-reply@example.com
```

## Fluxo Rapido

Suba as dependencias locais e execute a API:

```bash
make dev
```

Em outro terminal, chame a API diretamente no Quarkus dev mode:

```bash
curl -i -X POST http://localhost:8080/avaliacao \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: local-test-1' \
  -d '{"descricao":"A aula estava confusa e nao consegui acompanhar o conteudo.","nota":2}'
```

## Fluxo De Integracao Local

Provisionar tudo no fakecloud:

```bash
make local-up
```

Obter a URL da API:

```bash
terraform -chdir=infra/environments/dev output -raw api_base_url
```

Executar smoke test:

```bash
make smoke
```

O smoke test usa o output `api_base_url` do Terraform quando ele existe. Se o output nao estiver disponivel, usa `API_BASE_URL` ou `http://localhost:8080` como fallback.

No fakecloud, `make terraform-dev-apply` tambem cria um stage `$default` por AWS CLI apos o `terraform apply`. Esse ajuste local evita que o prefixo `/dev` seja repassado no evento Lambda, mantendo as rotas Quarkus em `/avaliacao` e `/health`. O stage Terraform `dev` continua existindo para refletir o modelo de infraestrutura.

## Estado Atual Das Integracoes

O ambiente local ja consegue subir fakecloud e provisionar a infraestrutura modelada. O runtime Java ainda usa adapters em memoria/no-op para DynamoDB, SNS e SES, entao o modo integracao valida empacotamento, Terraform e disponibilidade da API, mas ainda nao comprova persistencia/publicacao/envio reais ate os adapters AWS serem implementados.

Quando os adapters reais forem adicionados, os mesmos comandos devem passar a exercitar DynamoDB, SNS, SES, EventBridge e Lambdas pelo fakecloud.

## Testes De Integracao

O perfil Maven `integration-test` executa o Maven Failsafe no ciclo `verify`:

```bash
./mvnw -B verify -Pintegration-test
```

Convencao recomendada para novos testes:

```text
*IT.java
```

Os testes de integracao devem usar as variaveis locais impressas por `make env` e assumir fakecloud em `http://localhost:4566`.

## Limpeza

Destruir recursos do fakecloud e parar containers:

```bash
make local-down
```

Parar apenas containers:

```bash
make fakecloud-down
```

O estado persistido do fakecloud fica em `.fakecloud/`, que ja e ignorado pelo Git.
