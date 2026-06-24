# Contexto Tecnico

## Estado Atual Verificado

O repositório ainda não contém aplicação executável. Existem documentação, contrato OpenAPI, backlog de implementação, Docker Compose para fakecloud e Terraform para provisionar a infraestrutura AWS planejada.

Arquivos relevantes existentes:

- `docs/Especificacao_Tecnica.md`: fonte principal de requisitos técnicos, arquitetura, regras e plano de implementação.
- `docs/openapi-feedback-api.yaml`: contrato OpenAPI 3.0.3 da API pública.
- `docker-compose.yml`: fakecloud local em `localhost:4566`.
- `infra/environments/dev` e `infra/environments/prod`: composições Terraform por ambiente.
- `infra/modules/*`: módulos Terraform para API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge e CloudWatch.
- `tasks/*.md`: backlog incremental para construir a aplicação e fechar qualidade, CI/CD, E2E e documentação.

Ainda não existem no repositório:

- `pom.xml` raiz ou Maven wrapper.
- módulos `apps/feedback-api`, `apps/critical-notifier`, `apps/weekly-report` com código versionado.
- `libs/shared-kernel`.
- pipeline `.github/workflows`.
- testes automatizados executáveis.

## Stack Planejada e Versões

Stack definida pela especificação e parcialmente refletida no Terraform:

- Java 21, fixado em `mise.toml` e runtime Lambda `java21` em `infra/modules/lambda/variables.tf`.
- Quarkus para Lambdas Java, com handler padrão `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.
- AWS Lambda, API Gateway HTTP API, DynamoDB, SNS, SES, EventBridge/CloudWatch Events e CloudWatch.
- Terraform `>= 1.6.0` com provider AWS `~> 5.0`.
- fakecloud para emular serviços AWS localmente.
- Ferramentas previstas, ainda não configuradas em build executável: Maven 3.9+, JUnit 5, Mockito, RestAssured e Testcontainers.

## Execução Local Disponível Hoje

Subir o emulador AWS local:

```bash
docker compose up -d
```

O serviço `fakecloud` expõe `localhost:4566` e habilita DynamoDB, SNS, SES, EventBridge, Lambda, logs, CloudWatch, IAM e API Gateway. O estado local fica em `.fakecloud/`, que é ignorado pelo Git.

Configuração AWS local esperada para comandos manuais:

```bash
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
```

## Terraform

Ambiente de desenvolvimento usa fakecloud via endpoints configurados em `infra/environments/dev/versions.tf`.

Comandos úteis:

```bash
terraform -chdir=infra/environments/dev init
terraform -chdir=infra/environments/dev validate
terraform -chdir=infra/environments/dev plan -var="admin_email_to=admin@example.com" -var="email_from=no-reply@example.com"
```

O ambiente `prod` usa o provider AWS sem endpoints locais. Variáveis obrigatórias em `dev` e `prod`:

- `admin_email_to`
- `email_from`

Variáveis e padrões importantes:

- `aws_region`: `us-east-1`.
- `log_level`: `INFO`.
- `weekly_report_schedule_expression`: `cron(59 23 ? * SUN *)`.
- `lambda_handler`: `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.
- artefatos Lambda esperados: `apps/feedback-api/target/function.zip`, `apps/critical-notifier/target/function.zip`, `apps/weekly-report/target/function.zip`.
- CORS documental de produção até existir domínio real: `https://feedback.example.com`.
- `ADMIN_EMAIL_TO` documental até existir caixa real: `admin-feedback@example.com`.

Risco atual: `terraform plan/apply` para Lambdas exige que os zips existam, pois o módulo usa `filebase64sha256(var.artifact_path)`. Enquanto a aplicação não for empacotada, o plano pode falhar nesses pontos.

O cron UTC atual é aceitável no MVP. Migração para EventBridge Scheduler com timezone fica fora do escopo até `America/Sao_Paulo` virar requisito obrigatório.

Recomendação operacional para pipeline futuro: gerar `terraform plan` e exigir aprovação manual antes de `apply`, principalmente em `prod`. Auto-apply só deve ser considerado para `dev` controlado.

## Recursos de Infraestrutura Modelados

`infra/environments/*/main.tf` instancia:

- DynamoDB `feedbacks-<environment>`.
- SNS `feedback-critical-topic-<environment>`.
- SES identities para `email_from` e, quando diferente, `admin_email_to`.
- Lambda `feedback-api-<environment>`.
- Lambda `critical-notifier-<environment>`.
- Lambda `weekly-report-<environment>`.
- HTTP API Gateway com stage igual ao ambiente.
- EventBridge/CloudWatch Events para relatório semanal.
- alarmes e dashboard CloudWatch.

Recurso planejado, ainda não modelado no Terraform: DynamoDB auxiliar `feedback-processing-control-<environment>` para idempotência de notificações e relatórios.

Permissões IAM são separadas por Lambda:

- `feedback-api`: `dynamodb:PutItem` e `sns:Publish`.
- `critical-notifier`: `ses:SendEmail` e `ses:SendRawEmail`.
- `weekly-report`: `dynamodb:Query`, `dynamodb:Scan`, `ses:SendEmail` e `ses:SendRawEmail`.

## API Pública

Contrato versionado em `docs/openapi-feedback-api.yaml`.

Endpoints definidos:

- `POST /avaliacao`: registra feedback.
- `GET /health`: verifica disponibilidade da aplicação.

`POST /avaliacao` recebe:

```json
{
  "descricao": "A aula estava confusa e não consegui acompanhar o conteúdo.",
  "nota": 2
}
```

Resposta `201` esperada:

```json
{
  "id": "018f7e7a-5c45-7a24-8de1-f8ff2b7d4f91",
  "status": "CREATED",
  "urgencia": "CRITICA",
  "dataEnvio": "2026-05-31T13:00:00Z"
}
```

O header `X-Correlation-Id` é opcional na entrada e deve ser retornado na resposta.

## Persistência

Tabela DynamoDB definida em `infra/modules/dynamodb/main.tf`:

- tabela: nome configurável, usado como `feedbacks-<environment>` nos ambientes.
- billing: `PAY_PER_REQUEST`.
- partition key: `id` string.
- GSI `dataEnvio-index` com partition key `periodo` e sort key `dataEnvio`.
- server-side encryption habilitada.
- point-in-time recovery habilitado.

Item lógico especificado:

- `id`: UUID gerado pelo backend.
- `descricao`: texto do estudante.
- `nota`: inteiro de 0 a 10.
- `urgencia`: `CRITICA`, `MEDIA` ou `BAIXA`.
- `dataEnvio`: timestamp ISO-8601 gerado pelo backend.
- `periodo`: semana ISO fechada no formato `AAAA-Www`, usada para relatório semanal.
- `correlationId`: rastreamento de requisição.

Tabela auxiliar planejada para idempotência:

- nome: `feedback-processing-control-<environment>`.
- uso: registrar operações concluídas antes de enviar e-mails assíncronos.
- chave sugerida: string de controle, por exemplo `critical-notification#<feedbackId>` ou `weekly-report#<periodo>`.
- escrita: condicional, falhando quando a chave já existir para evitar reenvio.
- TTL: fora do MVP.
- reenvio manual forçado de relatório: fora do MVP.

## Observabilidade

Requisitos da especificação:

- logs estruturados em JSON.
- `correlationId` em requisições, eventos, persistência e logs.
- erros com contexto suficiente, sem expor dados sensíveis.
- métricas de negócio no namespace `FeedbackPlatform`.

Infraestrutura já modelada:

- log group por Lambda com retenção padrão de 14 dias.
- alarmes para `Errors` e `Throttles` de cada Lambda.
- alarmes para `NotificationFailureCount` e `WeeklyReportFailureCount`.
- dashboard `feedback-platform-<environment>` com invocações, erros e métricas de negócio.

## Testes e Qualidade

Não há testes executáveis hoje. A especificação prevê:

- unitários para validação, classificação de urgência, domínio e erros.
- integração com fakecloud/Testcontainers para DynamoDB, SNS, SES e relatório.
- contrato/API com RestAssured.
- E2E cobrindo feedback crítico, persistência, SNS, e-mail e relatório semanal.

Comandos Maven como `./mvnw test`, `./mvnw verify` e `./mvnw clean package` ainda não são executáveis até a criação da fundação Maven.

## Restrições, Riscos e Pendências

- Criar `pom.xml`, Maven wrapper, módulos de aplicação e `libs/shared-kernel`.
- Garantir que o build gere os zips nos caminhos esperados pelo Terraform.
- Implementar contrato OpenAPI sem usar a forma acentuada `/avaliação`.
- Criar a tabela auxiliar `feedback-processing-control-<environment>` no Terraform.
- Implementar idempotência de notificação crítica por `feedbackId` e do relatório semanal por `periodo` usando a tabela auxiliar.
- Enviar relatório semanal mesmo sem feedbacks, com contadores zerados.
- Configurar CI/CD real em `.github/workflows`.
- Validar se fakecloud cobre todos os recursos Terraform usados, especialmente API Gateway HTTP API, Lambda e CloudWatch Dashboard.
- Substituir o placeholder de CORS `https://feedback.example.com` pelo domínio real de produção quando existir; `prod` não deve usar `*`.
- Manter endpoint público sem autenticação apenas para o escopo acadêmico; para produção real, avaliar API key, JWT ou Cognito.
- Substituir o placeholder `admin-feedback@example.com` pelo e-mail ou grupo administrativo real quando existir.
