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

Atualmente o repositório concentra a documentação técnica, o contrato OpenAPI, a modelagem Terraform e o backlog de implementação. A estrutura das aplicações Java/Quarkus ainda está em construção.

## Documentação

Os documentos de referência do projeto estão disponíveis em [`docs/`](docs/), incluindo a especificação técnica e o contrato OpenAPI da API de feedback.

## Execução Local Da Feedback API

O fluxo local principal agora usa o `Makefile` para reduzir passos manuais. Para desenvolvimento da API, suba o fakecloud e execute a `feedback-api` diretamente pelo Quarkus:

```bash
make dev
```

Comandos equivalentes, caso queira executar manualmente:

```bash
docker compose up -d
./mvnw -pl apps/feedback-api -am quarkus:dev
```

Com a aplicação em execução, use `localhost:8080`:

```bash
curl -i http://localhost:8080/health
```

```bash
curl -i -X POST http://localhost:8080/avaliacao \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: local-test-1' \
  -d '{"descricao":"A aula estava confusa e nao consegui acompanhar o conteudo.","nota":2}'
```

O Terraform do ambiente `dev` continua modelando API Gateway e Lambda com o pacote Maven `apps/feedback-api/target/function.zip`, mas o endpoint local `execute-api.localhost.localstack.cloud:4566` nao e o fluxo recomendado para testar a `feedback-api`. O fakecloud pode entregar eventos incompatíveis com `quarkus-amazon-lambda-http`, causando erro `Missing HTTP method in request event`.

## Comandos De Desenvolvimento

Use `make help` para listar os comandos disponiveis. Os principais fluxos sao:

| Objetivo | Comando |
| --- | --- |
| Instalar ferramentas via mise | `make setup` |
| Subir fakecloud | `make up` |
| Aguardar fakecloud | `make wait` |
| Rodar Feedback API em modo dev | `make dev` |
| Rodar testes rapidos | `make test` |
| Rodar testes de um modulo | `make test-module MODULE=apps/feedback-api` |
| Rodar testes de integracao `*IT` | `make integration` |
| Rodar validacao regressiva local | `make regression` |
| Validar Terraform | `make infra-validate` |
| Aplicar Terraform dev no fakecloud | `make infra-bootstrap` |
| Limpar estado local do fakecloud | `make clean-local` |

`make regression` executa validacao OpenAPI, validacao Terraform e `./mvnw clean verify -Pregression`. O perfil Maven `regression` esta preparado para executar testes de integracao nomeados como `*IT.java` pelo Failsafe.

Para provisionar a infraestrutura local modelada em Terraform contra o fakecloud, use:

```bash
make infra-bootstrap
```

Por padrao, esse comando usa e-mails locais ficticios. Para sobrescrever:

```bash
make infra-bootstrap ADMIN_EMAIL_TO=admin@example.com EMAIL_FROM=no-reply@example.com
```

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
