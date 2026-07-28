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
- AWS CLI para comandos auxiliares do fluxo fakecloud local.
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
PROCESSING_CONTROL_TABLE_NAME=feedback-processing-control-dev
CRITICAL_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:feedback-critical-topic-dev
ADMIN_EMAIL_TO=admin@example.com
EMAIL_FROM=no-reply@example.com
LOG_LEVEL=INFO
```

Atencao: `make env`/`scripts/local-env.sh` ainda nao imprime `PROCESSING_CONTROL_TABLE_NAME` nem `LOG_LEVEL`. Os valores acima seguem os nomes definidos pelo Terraform e sao necessarios para executar `weekly-report` diretamente fora da Lambda provisionada.

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

O smoke test usa o output `api_base_url` do Terraform quando ele existe. Se o output nao estiver disponivel, usa `API_BASE_URL` ou `http://localhost:8080` como fallback. O modulo Terraform declara o stage `$default`; apos o apply, `scripts/fakecloud-default-stage.sh` garante esse stage no fakecloud, mantendo `/avaliacao` e `/health` sem prefixo.

## Estado Atual Das Integracoes

O ambiente local ja consegue subir fakecloud e provisionar a infraestrutura modelada. `critical-notifier` e `weekly-report` ja possuem testes de integracao reais contra fakecloud via Testcontainers, exercitando DynamoDB e SES diretamente. O `feedback-api` ainda persiste em memoria e o fluxo de notificacao critica ainda usa adapters no-op para SNS/SES; o IT da API (`AvaliacaoResourceIT`) valida o comportamento HTTP do aplicativo empacotado com repositorio em memoria e publicador no-op.

O pipeline unificado `POST /avaliacao -> DynamoDB -> SNS -> critical-notifier` permanece adiado. Quando os adapters reais forem adicionados, os mesmos comandos devem passar a exercitar DynamoDB, SNS, SES, EventBridge e Lambdas pelo fakecloud de ponta a ponta.

## Testes De Integracao

O perfil Maven `integration-test` executa o Maven Failsafe no ciclo `verify`. Testcontainers inicia automaticamente um container fakecloud versao-pinhada e o para ao final; nenhuma instancia fakecloud externa e necessaria:

```bash
make test-it
# ou diretamente:
./mvnw -B verify -Pintegration-test
```

Requisito: Docker deve estar acessivel. O comando nao depende de `fakecloud-up`, `AWS_ENDPOINT_URL` exportado ou Terraform apply.

Convencao recomendada para novos testes:

```text
*IT.java
```

Os testes de integracao criam tabelas DynamoDB e identidades SES com nomes unicos, usam portas mapeadas aleatoriamente e limpiam recursos ao final. Nao dependem de estado persistido ou Terraform apply.

### Isolamento de Recursos

Cada modulo AWS habilitado (`critical-notifier`, `weekly-report`) compartilha um unico container fakecloud para sua execucao Failsafe. As classes de teste criam tabelas e identidades SES com nomes unicos por execucao e nao dependem de estado fakecloud persistido entre execucoes.

## Testes E2E

A validacao E2E usa o fakecloud persistente e a stack Terraform local:

```bash
make e2e
```

Prerequisitos: `make local-up` deve ter sido executado previamente. O script:

1. Chama `POST /avaliacao` via API Gateway e valida a resposta HTTP.
2. Invoca o Lambda `critical-notifier` com um evento controlado.
3. Semeia dados semanais e invoca o Lambda `weekly-report`.
4. Inspeciona estado DynamoDB e emails SES.

**Fluxo adiado**: o pipeline `POST /avaliacao -> DynamoDB -> SNS -> critical-notifier` ainda nao esta conectado. Apenas os caminhos individualmente conectados sao validados.

### Solucao de Problemas

- **Docker nao disponivel**: `make test-it` e `make e2e` falham com mensagem clara. Verifique se o Docker esta rodando.
- **Pull da imagem fakecloud falha**: verifique conectividade de rede e se `ghcr.io` esta acessivel. A imagem e fixada em `0.44.6` no POM raiz.
- **Portas em conflito**: Testcontainers usa portas mapeadas aleatoriamente; conflitos sao improvaveis. Se ocorrerem, reinicie o Docker.
- **Estado persistido do fakecloud**: o diretorio `.fakecloud/` mantem estado entre execucoes do `make local-up`. Use `make local-down` para limpar.

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
