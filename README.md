# Tech Challenge IV

Este repositório contém a solução proposta para o Tech Challenge IV: uma plataforma serverless de feedback educacional para coletar avaliações de estudantes, classificar automaticamente a urgência de cada feedback e apoiar a atuação administrativa com notificações e relatórios.

A solução é orientada a eventos e combina uma API pública para registro de avaliações, persistência em DynamoDB, publicação de eventos para feedbacks críticos e processamento assíncrono para envio de e-mails administrativos. Também está previsto um relatório semanal consolidado com indicadores de notas, volume por período e destaques de feedbacks críticos.

## Visão Geral

- `feedback-api`: recebe `POST /avaliacao`, valida o payload, classifica a urgência, persiste o feedback e publica eventos críticos.
- `critical-notifier`: consome eventos de feedback crítico e envia notificação por e-mail via SES.
- `weekly-report`: executa de forma agendada, consolida os feedbacks da semana e envia relatório administrativo.
- `infra/`: define a infraestrutura AWS com Terraform para API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge e CloudWatch.

O projeto usa Java 21, Quarkus, Terraform e serviços AWS, com suporte a execução local dos serviços cloud por meio do fakecloud.

## Estado Atual

O repositório já possui aplicações Java/Quarkus multi-módulo, contrato OpenAPI, testes automatizados e infraestrutura Terraform. Os `critical-notifier` e `weekly-report` possuem testes de integração reais contra fakecloud via Testcontainers, exercitando DynamoDB e SES. A API de feedback ainda persiste em memória e os fluxos SNS/SES usam implementações no-op; o pipeline `POST -> DynamoDB -> SNS -> notifier` permanece adiado.

## Desenvolvimento Local

O guia completo de ambiente local está em [`docs/development-environment.md`](docs/development-environment.md).

Comandos principais:

```bash
make help          # Lista todos os comandos disponíveis
make test          # Testes unitários (Docker-free, Surefire)
make test-it       # Testes de integração (Testcontainers, requer Docker)
make e2e           # Validação E2E contra fakecloud persistente + Terraform
make dev           # Quarkus dev mode com fakecloud
make local-up      # Empacota Lambdas + Terraform apply
make smoke         # Smoke test rápido contra API
make verify        # Suite completa: test + package + openapi + terraform
make local-down    # Destrói stack local e para containers
```

### Níveis de Teste

| Nível | Comando | Lifecycle | Docker | Escopo |
|-------|---------|-----------|--------|--------|
| Unitário | `make test` | Surefire | Não | Domínio, use cases, adapters mock, HTTP contract |
| Integração | `make test-it` | Failsafe + Testcontainers | Sim | DynamoDB real, SES real, ciclo completo do Lambda |
| E2E | `make e2e` | Script externo | Sim | API HTTP, notifier Lambda, weekly report, DynamoDB, SES |

- **Unitários**: executam `*Test.java`, excluem `*IT.java`, não precisam de Docker.
- **Integração**: executam `*IT.java` via `./mvnw -B verify -Pintegration-test`; Testcontainers inicia e para o fakecloud automaticamente.
- **E2E**: usam `make local-up` (fakecloud persistente + Terraform) e chamadas HTTP/Lambda diretas. O fluxo unificado `POST -> DynamoDB -> SNS -> notifier` permanece adiado até adapters reais existirem.

## Documentação

Os documentos de referência do projeto estão disponíveis em [`docs/`](docs/), incluindo a especificação técnica e o contrato OpenAPI da API de feedback.

## CI Inicial

O workflow inicial de integração contínua está em [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Ele roda automaticamente em pull requests para a branch `main`, em pushes na `main` e também pode ser executado manualmente pelo GitHub Actions com `workflow_dispatch`.

Nesta fase o CI valida build/testes Java, formatação e validação Terraform, além do contrato OpenAPI. Ele não executa deploy, `terraform apply`, testes de integração com serviços AWS/fakecloud nem depende de secrets AWS.

Os comandos locais equivalentes são:

```bash
./mvnw -B clean package
terraform fmt -check -recursive infra
terraform -chdir=infra/environments/dev init -backend=false
terraform -chdir=infra/environments/dev validate
terraform -chdir=infra/environments/prod init -backend=false
terraform -chdir=infra/environments/prod validate
npx --yes @apidevtools/swagger-cli@4.0.4 validate docs/openapi-feedback-api.yaml
```

Para validar Terraform localmente sem gerar os pacotes Lambda reais, crie antes arquivos placeholder nos caminhos `apps/*/target/function.zip`, pois os módulos Lambda calculam `filebase64sha256` desses artefatos durante a validação.

## Board

O acompanhamento das atividades está no GitHub Projects:

[board](https://github.com/users/HugoOliveiraSoares/projects/4/views/1)
