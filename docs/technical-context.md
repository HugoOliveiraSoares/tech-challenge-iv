# Contexto Tecnico

## Estado Atual Verificado

O repositorio contem uma aplicacao Java/Quarkus multi-modulo em estagio inicial, infraestrutura Terraform e contratos/documentacao do Tech Challenge IV. O codigo executavel existe, mas as integracoes reais com AWS ainda nao estao implementadas nos adapters Java.

Arquivos e modulos principais:

- `pom.xml`: projeto Maven agregador `feedback-platform`.
- `libs/shared-kernel`: regras compartilhadas de urgencia (`Urgencia`, `UrgenciaClassifier`).
- `apps/feedback-api`: API REST Quarkus para `POST /avaliacao` e `GET /health`.
- `apps/critical-notifier`: Lambda Quarkus para processar notificacoes criticas, hoje com envio de e-mail no-op.
- `apps/weekly-report`: Lambda Quarkus para relatorio semanal, hoje com envio de relatorio no-op.
- `infra/environments/dev` e `infra/environments/prod`: composicoes Terraform por ambiente.
- `infra/modules/*`: modulos Terraform para API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge e CloudWatch.
- `docs/openapi-feedback-api.yaml`: contrato OpenAPI 3.0.3 da API publica.
- `docs/Especificacao_Tecnica.md`: especificacao ampla do desafio; contem partes ainda aspiracionais em relacao ao codigo atual.

## Stack e Versoes

- Java 21, fixado em `mise.toml` e usado por `maven.compiler.release=21`.
- Maven Wrapper versionado em `mvnw` e `.mvn/wrapper/maven-wrapper.properties`.
- Quarkus `3.15.3`, via BOM no `pom.xml` raiz.
- Lambda runtime Terraform: `java21` em `infra/modules/lambda/variables.tf`.
- Terraform `>= 1.6.0` com provider AWS `~> 5.0`.
- fakecloud local em `localhost:4566` para DynamoDB, SNS, SES, EventBridge, Lambda, logs, CloudWatch, IAM e API Gateway.
- Testes com JUnit 5, Quarkus JUnit e RestAssured onde aplicavel.

Dependencias relevantes por modulo:

- `feedback-api`: `quarkus-amazon-lambda-http`, `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `shared-kernel`, `rest-assured` em testes.
- `critical-notifier`: `quarkus-amazon-lambda`, `quarkus-jackson`, `shared-kernel`.
- `weekly-report`: `quarkus-amazon-lambda`, `quarkus-jackson`, `shared-kernel`.
- `shared-kernel`: JUnit 5 e `jboss-logmanager` para testes.

## Execucao Local

Instalar/selecionar Java com `mise`, quando usado:

```bash
mise install
```

Subir o emulador AWS local:

```bash
docker compose up -d
```

O estado local do fakecloud fica em `.fakecloud/`, ignorado pelo Git.

Executar a API em modo dev Quarkus:

```bash
./mvnw -pl apps/feedback-api quarkus:dev
```

Exemplo de chamada local da API:

```bash
curl -i -X POST http://localhost:8080/avaliacao \
  -H 'Content-Type: application/json' \
  -d '{"descricao":"A aula estava confusa e nao consegui acompanhar o conteudo.","nota":2}'
```

Health check local:

```bash
curl -i http://localhost:8080/health
```

Observacao: a API atual persiste em memoria (`InMemoryFeedbackGateway`) e apenas loga a publicacao critica (`NoOpCriticalFeedbackPublisher`). Rodar localmente a API nao exercita DynamoDB nem SNS.

## Comandos de Build e Teste

Comandos Maven disponiveis no estado atual:

```bash
./mvnw test
./mvnw -pl libs/shared-kernel test
./mvnw -pl apps/feedback-api test
./mvnw -pl apps/critical-notifier test
./mvnw -pl apps/weekly-report test
./mvnw clean package
```

O build Quarkus deve gerar os artefatos Lambda esperados pelo Terraform:

- `apps/feedback-api/target/function.zip`
- `apps/critical-notifier/target/function.zip`
- `apps/weekly-report/target/function.zip`

Nao ha configuracao de lint, formatador, cobertura ou pipeline CI/CD versionada.

## Terraform

`dev` aponta o provider AWS para o fakecloud em `infra/environments/dev/versions.tf`. `prod` usa provider AWS real sem endpoints locais.

Comandos uteis:

```bash
terraform -chdir=infra/environments/dev init
terraform -chdir=infra/environments/dev validate
terraform -chdir=infra/environments/dev plan -var="admin_email_to=admin@example.com" -var="email_from=no-reply@example.com"
```

Variaveis obrigatorias em `dev` e `prod`:

- `admin_email_to`
- `email_from`

Padroes importantes:

- `aws_region`: `us-east-1`.
- `log_level`: `INFO`.
- `weekly_report_schedule_expression`: `cron(59 23 ? * SUN *)`.
- `lambda_handler`: `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.
- CORS em `dev`: `[*]`.
- CORS em `prod`: lista vazia por padrao; exige dominios explicitos antes de uso por browser.

O modulo Lambda calcula `filebase64sha256(var.artifact_path)`, entao `plan/apply` completo falha se os zips nao existirem.

## Estrutura Tecnica

```text
.
+-- apps/
|   +-- feedback-api/
|   |   +-- src/main/java/br/com/fiap/feedbackapi/{core,infra}/
|   +-- critical-notifier/
|   |   +-- src/main/java/br/com/fiap/criticalnotifier/{core,infra}/
|   +-- weekly-report/
|       +-- src/main/java/br/com/fiap/weeklyreport/{core,infra}/
+-- libs/shared-kernel/
|   +-- src/main/java/br/com/fiap/feedbackplatform/shared/
+-- infra/
|   +-- environments/{dev,prod}/
|   +-- modules/{api-gateway,cloudwatch,dynamodb,eventbridge,lambda,ses,sns}/
+-- docs/
```

Padrao de camadas observado no codigo Java:

- `core/domain`: records e tipos de dominio.
- `core/dto`: comandos de caso de uso quando necessario.
- `core/gateway`: ports/interfaces de saida.
- `core/usecase`: casos de uso com regra de aplicacao.
- `infra/http`: recursos REST da API.
- `infra/lambda`: handlers Lambda diretos.
- `infra/gateway/*`: adapters de infraestrutura; hoje `InMemory` ou `NoOp`.
- `infra/config`: produtores/configuracao CDI.

## API Publica

Contrato versionado: `docs/openapi-feedback-api.yaml`.

Endpoints implementados em `apps/feedback-api`:

- `POST /avaliacao`: valida `descricao` e `nota`, classifica urgencia, gera `id` e `dataEnvio`, salva em memoria e retorna `201`.
- `GET /health`: retorna `{ "status": "UP" }`.

Request valido:

```json
{
  "descricao": "A aula estava confusa e nao consegui acompanhar o conteudo.",
  "nota": 2
}
```

Resposta atual esperada:

```json
{
  "id": "uuid-gerado",
  "status": "CREATED",
  "urgencia": "CRITICA",
  "dataEnvio": "timestamp-utc"
}
```

Validacoes implementadas por Bean Validation:

- `descricao`: `@NotBlank`, tamanho minimo 10 e maximo 1000.
- `nota`: `@NotNull`, minimo 0 e maximo 10.

Lacuna atual: o OpenAPI documenta `X-Correlation-Id` e respostas de erro padronizadas, mas o codigo HTTP ainda nao implementa propagacao/retorno de correlation id nem modelo customizado de erro.

## Persistencia e Integracoes

Persistencia planejada no Terraform:

- DynamoDB `feedbacks-<environment>`.
- Billing `PAY_PER_REQUEST`.
- Chave primaria `id` string.
- GSI `dataEnvio-index` com partition key `periodo` e sort key `dataEnvio`.
- Point-in-time recovery e server-side encryption habilitados.

Persistencia implementada no codigo:

- `feedback-api` usa `InMemoryFeedbackGateway` com `ConcurrentLinkedQueue`.
- O dominio `Feedback` contem `id`, `descricao`, `nota`, `urgencia` e `dataEnvio`.
- O campo `periodo`, necessario para o GSI e relatorio semanal, ainda nao existe no modelo Java.

Integracoes planejadas no Terraform:

- API Gateway HTTP API integra com Lambda `feedback-api`.
- SNS `feedback-critical-topic-<environment>` invoca Lambda `critical-notifier`.
- SES valida identidades `email_from` e `admin_email_to` quando diferentes.
- EventBridge/CloudWatch Events aciona `weekly-report` semanalmente.

Integracoes implementadas no codigo:

- Publicacao SNS ainda e `NoOpCriticalFeedbackPublisher` com log.
- Envio SES de notificacao critica ainda e `NoOpEmailGateway` com log.
- Envio SES de relatorio semanal ainda e `NoOpReportEmailGateway` com log.
- `weekly-report` ainda nao consulta DynamoDB nem calcula metricas; apenas delega o request para o gateway de e-mail.

## Observabilidade e Erros

Infraestrutura modelada:

- Log group por Lambda com retencao padrao de 14 dias.
- Alarmes CloudWatch para `Errors` e `Throttles` de cada Lambda.
- Alarmes para metricas customizadas `NotificationFailureCount` e `WeeklyReportFailureCount`.
- Dashboard `feedback-platform-<environment>` com metricas de Lambda e negocio.

Codigo atual:

- Adapters no-op usam `org.jboss.logging.Logger`.
- Nao ha logs estruturados JSON configurados.
- Nao ha publicacao de metricas customizadas `FeedbackPlatform`.
- Tratamento de erros customizado ainda nao foi implementado; a API depende do comportamento padrao do Quarkus/Bean Validation.

## Testes Visiveis

Testes versionados:

- `UrgenciaClassifierTest`: cobre limites de classificacao e rejeicao de notas fora de 0..10.
- `AvaliacaoResourceTest`: cobre criacao minima de avaliacao critica via HTTP.
- `HealthResourceTest`: cobre `GET /health`.
- `NotifyCriticalFeedbackUseCaseTest`: cobre delegacao para gateway de e-mail.
- `GenerateWeeklyReportUseCaseTest`: cobre delegacao para gateway de relatorio.

Lacunas de qualidade:

- Sem testes de integracao com fakecloud/Testcontainers.
- Sem teste de contrato OpenAPI automatizado.
- Sem cobertura de erros HTTP customizados.
- Sem testes para DynamoDB, SNS, SES ou EventBridge reais/emulados.

## Restricoes, Riscos e Pendencias

- Implementar adapters reais para DynamoDB, SNS e SES.
- Adicionar `periodo` ao modelo persistido e gerar semana ISO para o relatorio.
- Implementar consulta DynamoDB do `weekly-report`, preferencialmente pelo GSI `dataEnvio-index`.
- Definir e versionar o contrato do evento SNS de feedback critico.
- Implementar `X-Correlation-Id` de ponta a ponta.
- Implementar respostas de erro no formato do OpenAPI.
- Implementar metricas customizadas esperadas pelos alarmes/dashboard.
- Avaliar DLQ/retry para fluxos assincronos; nao ha DLQ hoje.
- Definir dominio real de CORS para producao.
- Definir e-mails reais verificados no SES para `email_from` e `admin_email_to`.
- Criar pipeline CI/CD em `.github/workflows`, se necessario.
