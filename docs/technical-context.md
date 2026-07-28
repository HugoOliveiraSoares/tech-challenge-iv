# Contexto Tecnico

## Estado Atual Verificado

O repositorio contem uma aplicacao Java/Quarkus multi-modulo, infraestrutura Terraform e contrato OpenAPI para uma plataforma serverless de feedback educacional. O codigo executavel cobre os tres componentes principais. O `feedback-api` usa DynamoDB e SNS, o `critical-notifier` usa SNS, DynamoDB e SES, e o `weekly-report` usa DynamoDB e SES.

Modulos principais:

- `libs/shared-kernel`: dominio e ports compartilhados (`Feedback`, `CriticalFeedbackEvent`, `PeriodoIsoWeek`, `Urgencia`, `UrgenciaClassifier`, `FeedbackRepository`, `CriticalFeedbackPublisher`).
- `apps/feedback-api`: API REST Quarkus para `POST /avaliacao` e `GET /health`.
- `apps/critical-notifier`: Lambda Quarkus para notificacao de feedback critico com envelope SNS, idempotencia DynamoDB e envio SES.
- `apps/weekly-report`: Lambda Quarkus para relatorio semanal com consulta DynamoDB, idempotencia e envio SES.
- `infra/environments/dev`: composicao Terraform apenas para execucao local com fakecloud em `localhost:4566`; nao deve ser usada para recursos AWS reais.
- `infra/environments/prod`: composicao Terraform para AWS real, sem endpoints locais.
- `infra/modules/*`: modulos Terraform para API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge Scheduler e CloudWatch.

## Stack e Versoes

- Java 21, fixado em `mise.toml` e em `maven.compiler.release=21`.
- Maven Wrapper via `./mvnw`; `.mvn/wrapper/maven-wrapper.properties` baixa Maven 3.9.16.
- Quarkus 3.15.3 via BOM no `pom.xml` raiz.
- Maven Surefire/Failsafe 3.5.0; JUnit 5, Quarkus JUnit e RestAssured onde aplicavel.
- Terraform `>= 1.6.0` com provider AWS `~> 5.0`.
- Lambda runtime Terraform: `java21`; handler padrao: `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest`.
- fakecloud local em `localhost:4566` para DynamoDB, SNS, SES, EventBridge Scheduler, Lambda, logs, CloudWatch, IAM e API Gateway.

Dependencias por modulo:

- `feedback-api`: `quarkus-amazon-lambda-http`, `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, Quarkiverse DynamoDB Enhanced, Quarkiverse SNS, AWS SDK URL connection client, `shared-kernel`, RestAssured/Mockito em testes.
- `critical-notifier`: `quarkus-amazon-lambda`, `quarkus-jackson`, `quarkus-logging-json`, AWS SDK DynamoDB/SES, `aws-lambda-java-events`, `shared-kernel`, Testcontainers em testes de integracao.
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

O estado do fakecloud fica em `.fakecloud/`, ignorado pelo Git. Em modo local, `feedback-api`, `critical-notifier` e `weekly-report` usam `AWS_ENDPOINT_URL` para apontar clients AWS para fakecloud quando executados com as variaveis adequadas.

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

Para `terraform validate` sem build Java, o CI cria placeholders em `apps/*/target/function.zip`, pois o modulo Lambda calcula `filebase64sha256(var.artifact_path)`. Para validacao local pelos targets Make e para qualquer `plan/apply`, rode `make package` para gerar os zips corretos:

- `apps/feedback-api/target/function.zip`
- `apps/critical-notifier/target/function.zip`
- `apps/weekly-report/target/function.zip`

Nao ha configuracao versionada de lint/formatter Java, medicao de cobertura, migrations ou deploy automatizado. Existe seed local para o relatorio em `scripts/seed-feedbacks-dev.sh`/`make seed-feedbacks-dev`. O workflow `.github/workflows/ci.yml` roda testes Java de unidade, package Maven, validacao OpenAPI, `terraform fmt -check` e `terraform validate` para `dev` e `prod`; ele nao executa `terraform plan/apply`, nao reutiliza no job Terraform os zips gerados pelo job Maven e nao roda o perfil Failsafe de integracao com fakecloud.

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
- `core/usecase`: orquestracao de regra de aplicacao; os casos de uso usam CDI e, no relatorio, JBoss Logging/MDC, portanto o `core` nao e totalmente neutro a frameworks.
- `infra/http`: recursos REST e DTOs de transporte da API.
- `infra/lambda`: handlers Lambda diretos.
- `infra/gateway/*`: adapters de infraestrutura, incluindo DynamoDB, SNS e SES. Doubles em memoria aparecem em testes e o `InMemoryFeedbackGateway` permanece no codigo sem CDI ativo.
- `infra/config`: produtores/configuracao CDI.
- `shared-kernel`: conceitos de dominio/ports compartilhados por mais de um app.

## API Publica Implementada

Contrato versionado: `docs/openapi-feedback-api.yaml`.

- `POST /avaliacao`: valida `descricao` e `nota`, aceita `X-Correlation-Id`, classifica urgencia, gera `id`, `dataEnvio`, `periodo`, salva no DynamoDB, publica evento SNS quando a urgencia e `CRITICA` e retorna `201`.
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
- Tabela DynamoDB `feedback-processing-control-<environment>` com partition key `periodo`. O Terraform concede acesso ao notifier e ao relatorio; ambos a usam para controles idempotentes com formatos de chave adequados ao respectivo fluxo.
- Point-in-time recovery e server-side encryption habilitados.

Persistencia implementada no codigo:

- `feedback-api` usa `DynamoDbFeedbackRepository` como adapter CDI ativo para gravar feedbacks em DynamoDB; `InMemoryFeedbackGateway` permanece sem `@ApplicationScoped` e nao e o adapter runtime principal.
- `Feedback` contem `id`, `descricao`, `nota`, `urgencia`, `dataEnvio`, `periodo` e `correlationId`.
- `periodo` e calculado como semana ISO UTC (`AAAA-Www`) por `PeriodoIsoWeek`.

Integracoes modeladas no Terraform:

- API Gateway HTTP API integra com Lambda `feedback-api` para `POST /avaliacao` e `GET /health`.
- SNS `feedback-critical-topic-<environment>` invoca Lambda `critical-notifier`.
- SES cria identidades para `email_from` e, quando diferente, `admin_email_to`.
- EventBridge Scheduler aciona `weekly-report` pelo cron `cron(59 23 ? * SUN *)` em UTC, usando a mesma convencao do `periodo` persistido e do agrupamento diario.

Integracoes implementadas no codigo:

- SNS de feedback critico e `SnsCriticalFeedbackPublisher`, que publica `CriticalFeedbackEvent` serializado no topico configurado.
- `critical-notifier` parseia envelopes SNS com `SnsCriticalFeedbackEventParser`, usa `DynamoDbCriticalNotificationIdempotencyGateway` e envia e-mail por `SesCriticalEmailGateway`.
- SES de relatorio semanal envia e-mail via `SesReportEmailGateway`.
- `weekly-report` consulta DynamoDB pelo GSI `dataEnvio-index`, calcula indicadores semanais e usa tabela de controle. Somente `FAILED_BEFORE_SEND` permite retry automatico; `PROCESSING`, `SENT` e `FAILED_AFTER_SEND_ATTEMPT` bloqueiam nova execucao.
- Em `infra/environments/dev`, as Lambdas recebem endpoints/credenciais fakecloud quando precisam chamar AWS local. O `feedback-api` usa `host.docker.internal:4566` dentro da Lambda; `critical-notifier` usa o endpoint fakecloud configurado no ambiente; `weekly-report` tambem usa endpoint local para DynamoDB/SES.

Variaveis efetivamente consumidas pelo codigo Java:

- `feedback-api`: `FEEDBACK_TABLE_NAME`, `CRITICAL_TOPIC_ARN`, `AWS_REGION`, `AWS_ENDPOINT_URL`, credenciais AWS locais em perfil `%local` e `LOG_LEVEL`.
- `critical-notifier`: `PROCESSING_CONTROL_TABLE_NAME`, `ADMIN_EMAIL_TO`, `EMAIL_FROM`, `AWS_REGION`, `AWS_ENDPOINT_URL` e `LOG_LEVEL`.
- `weekly-report`: `FEEDBACK_TABLE_NAME`, `PROCESSING_CONTROL_TABLE_NAME`, `ADMIN_EMAIL_TO`, `EMAIL_FROM`, `AWS_REGION`, `AWS_ENDPOINT_URL` e `LOG_LEVEL`.

## Observabilidade e Operacao

Infraestrutura modelada:

- Log group por Lambda com retencao configuravel pelo modulo Lambda.
- Alarmes CloudWatch para `Errors` e `Throttles` de cada Lambda.
- Alarmes para metricas customizadas `NotificationFailureCount` e `WeeklyReportFailureCount` no namespace `FeedbackPlatform`.
- Dashboard `feedback-platform-<environment>` com metricas Lambda e metricas de negocio esperadas.
- Os alarmes usam atualmente o mesmo topico SNS dos eventos de feedback critico. Como o notifier interpreta mensagens SNS como eventos de feedback, esse acoplamento com mensagens CloudWatch e uma pendencia operacional critica.

Codigo atual:

- Use cases e adapters usam `org.jboss.logging.Logger`.
- `critical-notifier` e `weekly-report` configuram logs JSON no console; o relatorio usa campos MDC como `operation`, `periodo`, `status` e `feedback_count`.
- `feedback-api` nao configura logs JSON no momento.
- Nao ha publicacao de metricas customizadas `FeedbackPlatform`.

## Testes Visiveis

- `shared-kernel`: classificacao e limites de urgencia, invariantes/normalizacao de `Feedback`, semana ISO UTC e evento critico.
- `feedback-api`: sucesso `201`, health, geracao/reuso de correlation ID, validacoes `400`/`422`, JSON invalido, `404`/`415` preservados, erros de persistencia/notificacao, erro interno `500`, adapter DynamoDB e publisher SNS.
- `critical-notifier`: parser de envelope SNS, handler Lambda, use case, composicao de e-mail, gateway SES, producer AWS e gateway DynamoDB de idempotencia.
- `weekly-report`: agregacoes, periodo padrao UTC, envio sem feedbacks, bloqueio de duplicidade, retry antes do envio, bloqueio depois de tentativa ambigua, handler Lambda, reader DynamoDB, idempotencia DynamoDB e gateway SES.

Classes `*IT.java` existentes: `CriticalNotifierIT`, `DynamoDbCriticalNotificationIdempotencyGatewayIT` e `WeeklyReportIT`. Elas rodam pelo perfil Maven `integration-test`/Failsafe e usam Testcontainers com fakecloud. Ainda nao ha medicao de cobertura, nem validacao CI desse perfil, nem teste integrado do contrato real EventBridge Scheduler. O `make smoke` cobre somente o retorno `201` do endpoint HTTP.

## Referencias e Pendencias

- Ambiente local: [`development-environment.md`](development-environment.md).
- Deploy manual em AWS: [`aws-deployment.md`](aws-deployment.md).
- Contrato HTTP: [`openapi-feedback-api.yaml`](openapi-feedback-api.yaml).
- Riscos e tradeoffs: [`decisions-and-tradeoffs.md`](decisions-and-tradeoffs.md).
