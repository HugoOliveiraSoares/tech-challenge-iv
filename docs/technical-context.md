# Contexto Tecnico

## Estado Atual Verificado

O repositorio contem uma aplicacao Java/Quarkus multi-modulo, infraestrutura Terraform e contrato OpenAPI para uma plataforma serverless de feedback educacional. O codigo executavel cobre os tres componentes principais. O `feedback-api` e o `critical-notifier` ainda usam adapters in-memory/no-op para persistencia, SNS e e-mail critico; o `weekly-report` ja usa AWS SDK para DynamoDB e SES.

Modulos principais:

- `libs/shared-kernel`: dominio e ports compartilhados (`Feedback`, `CriticalFeedbackEvent`, `PeriodoIsoWeek`, `Urgencia`, `UrgenciaClassifier`, `FeedbackRepository`, `CriticalFeedbackPublisher`).
- `apps/feedback-api`: API REST Quarkus para `POST /avaliacao` e `GET /health`.
- `apps/critical-notifier`: Lambda Quarkus para notificacao de feedback critico; envio de e-mail ainda e no-op.
- `apps/weekly-report`: Lambda Quarkus para relatorio semanal com consulta DynamoDB, idempotencia e envio SES.
- `infra/environments/dev`: composicao Terraform apenas para execucao local com fakecloud em `localhost:4566`; nao deve ser usada para recursos AWS reais.
- `infra/environments/prod`: composicao Terraform para AWS real, sem endpoints locais.
- `infra/modules/*`: modulos Terraform para API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge/CloudWatch Events e CloudWatch.

## Stack e Versoes

- Java 21, fixado em `mise.toml` e em `maven.compiler.release=21`.
- Maven Wrapper via `./mvnw`; `.mvn/wrapper/maven-wrapper.properties` baixa Maven 3.9.16.
- Quarkus 3.15.3 via BOM no `pom.xml` raiz.
- Maven Surefire/Failsafe 3.5.0; JUnit 5, Quarkus JUnit e RestAssured onde aplicavel.
- Terraform `>= 1.6.0` com provider AWS `~> 5.0`.
- Lambda runtime Terraform: `java21`; handler padrao: `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.
- fakecloud local em `localhost:4566` para DynamoDB, SNS, SES, EventBridge, Lambda, logs, CloudWatch, IAM e API Gateway.

Dependencias por modulo:

- `feedback-api`: `quarkus-amazon-lambda-http`, `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `shared-kernel`, RestAssured em testes.
- `critical-notifier`: `quarkus-amazon-lambda`, `quarkus-jackson`, `shared-kernel`.
- `weekly-report`: `quarkus-amazon-lambda`, `quarkus-jackson`, `quarkus-logging-json`, AWS SDK DynamoDB/SES, `shared-kernel`.
- `shared-kernel`: JUnit 5 e `jboss-logmanager` para testes.

## Execucao Local

Instalar/selecionar Java quando necessario:

```bash
mise install
```

Subir o emulador AWS local:

```bash
docker compose up -d
```

O estado do fakecloud fica em `.fakecloud/`, ignorado pelo Git. A API local atual nao usa DynamoDB/SNS reais; ela persiste em memoria e loga publicacao critica. O `weekly-report` usa `AWS_ENDPOINT_URL` para apontar os clients DynamoDB/SES para fakecloud durante execucao local.

Para o fluxo local completo com Terraform, prefira os Make targets:

```bash
make local-up
make smoke
make local-down
```

`infra/environments/dev/` e local-only: o provider AWS desse ambiente usa credenciais `test` e endpoints fakecloud. Para AWS real, use `infra/environments/prod/` e variaveis apropriadas.

Executar a API em modo dev Quarkus:

```bash
./mvnw -pl apps/feedback-api -am quarkus:dev
```

Exemplo de chamada local:

```bash
curl -i -X POST http://localhost:8080/avaliacao \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: local-test-1' \
  -d '{"descricao":"A aula estava confusa e nao consegui acompanhar o conteudo.","nota":2}'
```

Health check:

```bash
curl -i http://localhost:8080/health
```

## Comandos de Desenvolvimento e Qualidade

Maven:

```bash
./mvnw test
./mvnw -pl libs/shared-kernel test
./mvnw -pl apps/feedback-api -am test
./mvnw -pl apps/critical-notifier -am test
./mvnw -pl apps/weekly-report -am test
./mvnw clean package
```

OpenAPI:

```bash
npx --yes @apidevtools/swagger-cli@4.0.4 validate docs/openapi-feedback-api.yaml
```

Terraform:

```bash
terraform fmt -check -recursive infra
terraform -chdir=infra/environments/dev init -backend=false
terraform -chdir=infra/environments/dev validate
terraform -chdir=infra/environments/prod init -backend=false
terraform -chdir=infra/environments/prod validate
```

Os targets `make terraform-dev-*` tambem sao locais e apontam para fakecloud. Eles existem para validar/provisionar a stack de desenvolvimento em maquina local, nao para deploy em uma conta AWS.

Para `terraform validate` sem build Java, crie placeholders em `apps/*/target/function.zip`; o CI faz isso porque o modulo Lambda calcula `filebase64sha256(var.artifact_path)`. Para `plan/apply` real, rode `./mvnw clean package` antes para gerar os zips corretos:

- `apps/feedback-api/target/function.zip`
- `apps/critical-notifier/target/function.zip`
- `apps/weekly-report/target/function.zip`

Nao ha configuracao versionada de lint Java, formatter Java, cobertura, migrations, seeds ou deploy automatizado. O workflow `.github/workflows/ci.yml` roda testes Java, package Maven, validacao OpenAPI, `terraform fmt -check` e `terraform validate` para `dev` e `prod`; ele nao executa `terraform apply` nem testes de integracao com fakecloud/AWS.

## Estrutura Tecnica

```text
apps/feedback-api/src/main/java/br/com/fiap/feedbackapi/{core,infra}/
apps/critical-notifier/src/main/java/br/com/fiap/criticalnotifier/{core,infra}/
apps/weekly-report/src/main/java/br/com/fiap/weeklyreport/{core,infra}/
libs/shared-kernel/src/main/java/br/com/fiap/feedbackplatform/shared/{domain,exception,port}/
infra/environments/{dev,prod}/
infra/modules/{api-gateway,cloudwatch,dynamodb,eventbridge,lambda,ses,sns}/
```

Padrao de camadas observado:

- `core/domain`: records e tipos de dominio especificos do app quando existem.
- `core/dto`: comandos de caso de uso quando necessario.
- `core/gateway`: ports/interfaces de saida locais ao app.
- `core/usecase`: orquestracao de regra de aplicacao.
- `infra/http`: recursos REST e DTOs de transporte da API.
- `infra/lambda`: handlers Lambda diretos.
- `infra/gateway/*`: adapters de infraestrutura; podem ser temporarios (`InMemory`/`NoOp`) ou adapters AWS reais, como os de DynamoDB/SES no `weekly-report`.
- `infra/config`: produtores/configuracao CDI.
- `shared-kernel`: conceitos de dominio/ports compartilhados por mais de um app.

## API Publica Implementada

Contrato versionado: `docs/openapi-feedback-api.yaml`.

- `POST /avaliacao`: valida `descricao` e `nota`, aceita `X-Correlation-Id`, classifica urgencia, gera `id`, `dataEnvio`, `periodo`, salva em memoria e retorna `201`.
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

Observacoes verificadas:

- `descricao`: `@NotBlank`, minimo 10 e maximo 1000 caracteres.
- `nota`: `@NotNull`, minimo 0 e maximo 10.
- `X-Correlation-Id` e reutilizado quando enviado ou gerado quando ausente, validado entre 8 e 100 caracteres, propagado para `Feedback`/`CriticalFeedbackEvent` e retornado no response.
- A API possui mappers customizados para validacao, JSON malformado/mapeamento invalido, regra de dominio e erro interno, usando `code`, `message`, `correlationId` e `details`.

## Persistencia e Integracoes

Persistencia modelada no Terraform:

- Tabela DynamoDB `feedbacks-<environment>` com billing `PAY_PER_REQUEST`.
- Chave primaria `id` string.
- GSI `dataEnvio-index` com partition key `periodo` e sort key `dataEnvio`.
- Tabela DynamoDB `feedback-processing-control-<environment>` para idempotencia do relatorio semanal por `periodo`.
- Point-in-time recovery e server-side encryption habilitados.

Persistencia implementada no codigo:

- `feedback-api` usa `InMemoryFeedbackGateway` com `ConcurrentLinkedQueue`.
- `Feedback` contem `id`, `descricao`, `nota`, `urgencia`, `dataEnvio`, `periodo` e `correlationId`.
- `periodo` e calculado como semana ISO UTC (`AAAA-Www`) por `PeriodoIsoWeek`.

Integracoes modeladas no Terraform:

- API Gateway HTTP API integra com Lambda `feedback-api` para `POST /avaliacao` e `GET /health`.
- SNS `feedback-critical-topic-<environment>` invoca Lambda `critical-notifier`.
- SES cria identidades para `email_from` e, quando diferente, `admin_email_to`.
- EventBridge/CloudWatch Events aciona `weekly-report` pelo cron `cron(59 23 ? * SUN *)`.

Integracoes implementadas no codigo:

- SNS ainda e `NoOpCriticalFeedbackPublisher` com log.
- SES de notificacao critica ainda e `NoOpEmailGateway` com log.
- SES de relatorio semanal envia e-mail via `SesReportEmailGateway`.
- `weekly-report` consulta DynamoDB pelo GSI `dataEnvio-index`, calcula metricas semanais e usa tabela de controle para evitar reenvio de periodos `SENT` e permitir retry de periodos `FAILED`.
- Em `infra/environments/dev`, somente a Lambda `weekly-report` recebe `AWS_ENDPOINT_URL`/credenciais fakecloud via Terraform porque e o unico app que hoje instancia clients AWS SDK diretamente. `feedback-api` e `critical-notifier` recebem variaveis de recurso, mas seus adapters atuais nao usam SDK real.

## Observabilidade e Operacao

Infraestrutura modelada:

- Log group por Lambda com retencao configuravel pelo modulo Lambda.
- Alarmes CloudWatch para `Errors` e `Throttles` de cada Lambda.
- Alarmes para metricas customizadas `NotificationFailureCount` e `WeeklyReportFailureCount` no namespace `FeedbackPlatform`.
- Dashboard `feedback-platform-<environment>` com metricas Lambda e metricas de negocio esperadas.

Codigo atual:

- Adapters no-op e use cases usam `org.jboss.logging.Logger`.
- `weekly-report` configura logs JSON no console e campos MDC como `operation`, `periodo`, `status` e `feedback_count`.
- `feedback-api` e `critical-notifier` nao configuram logs JSON no momento.
- Nao ha publicacao de metricas customizadas `FeedbackPlatform`.

## Testes Visiveis

- `UrgenciaClassifierTest`: limites da classificacao e rejeicao de notas fora de 0..10.
- `FeedbackTest`: criacao, normalizacao, validacao de descricao, periodo, urgencia e correlation id.
- `PeriodoIsoWeekTest`: semana ISO UTC e virada de ano.
- `CriticalFeedbackEventTest`: validacao e normalizacao do evento critico.
- `AvaliacaoResourceTest`: criacao minima via HTTP retornando `201` e urgencia `CRITICA`.
- `HealthResourceTest`: `GET /health`.
- `NotifyCriticalFeedbackUseCaseTest`: delegacao para gateway de e-mail.
- `GenerateWeeklyReportUseCaseTest`: agregacoes semanais, periodo padrao, envio sem feedbacks, bloqueio de duplicidade e retry apenas para falha antes da tentativa de envio.
