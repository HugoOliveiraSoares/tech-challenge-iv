# Contexto Tecnico

## Estado Atual Verificado

O repositorio contem uma aplicacao Java/Quarkus multi-modulo, infraestrutura Terraform e contrato OpenAPI para uma plataforma serverless de feedback educacional. O codigo executavel ja cobre os tres componentes principais, mas as integracoes AWS reais ainda nao foram implementadas nos adapters Java.

Modulos principais:

- `libs/shared-kernel`: dominio e ports compartilhados (`Feedback`, `CriticalFeedbackEvent`, `PeriodoIsoWeek`, `Urgencia`, `UrgenciaClassifier`, `FeedbackRepository`, `CriticalFeedbackPublisher`).
- `apps/feedback-api`: API REST Quarkus para `POST /avaliacao` e `GET /health`.
- `apps/critical-notifier`: Lambda Quarkus para notificacao de feedback critico; envio de e-mail ainda e no-op.
- `apps/weekly-report`: Lambda Quarkus para relatorio semanal; consulta/calculo/envio real ainda nao existem.
- `infra/environments/{dev,prod}`: composicoes Terraform por ambiente.
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
- `weekly-report`: `quarkus-amazon-lambda`, `quarkus-jackson`, `shared-kernel`.
- `shared-kernel`: JUnit 5 e `jboss-logmanager` para testes.

## Execucao Local

### Modo Desenvolvimento

Instalar/selecionar Java quando necessario:

```bash
mise install
```

Subir o emulador AWS local:

```bash
docker compose up -d
```

O estado do fakecloud fica em `.fakecloud/`, ignorado pelo Git. A API local atual nao usa DynamoDB/SNS reais; ela persiste em memoria e loga publicacao critica.

Executar a `feedback-api` em modo dev Quarkus:

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

Este e o fluxo recomendado para desenvolver e testar manualmente a API local: fakecloud fornece os servicos AWS emulados, enquanto a aplicacao HTTP roda em `localhost:8080` pelo Quarkus.

### Modo Infra Local

O Terraform em `infra/environments/dev` modela API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge e CloudWatch contra o fakecloud em `localhost:4566`. As Lambdas usam os pacotes Maven gerados em `apps/*/target/function.zip`; para `plan/apply` real, gere esses artefatos antes:

```bash
./mvnw clean package
terraform -chdir=infra/environments/dev apply
```

O endpoint `api_base_url` do Terraform pode ser criado pelo fakecloud, mas nao deve ser usado como fluxo principal para testar a `feedback-api` local. Em teste manual, o API Gateway do fakecloud invocou a Lambda Java com um evento sem metodo HTTP, e o `quarkus-amazon-lambda-http` falhou com:

```text
java.lang.IllegalStateException: Missing HTTP method in request event
```

Isso indica incompatibilidade no formato do evento entregue pelo fakecloud/API Gateway local, nao erro de build Maven nem ausencia do handler Java. Para validar comportamento HTTP da API, use `localhost:8080` com `quarkus:dev`. Para validar empacotamento Lambda, use os zips Maven e invoque a Lambda com um evento API Gateway compativel.

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
- `infra/gateway/*`: adapters de infraestrutura; hoje `InMemory` ou `NoOp`.
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
- `X-Correlation-Id` e recebido, normalizado e propagado para `Feedback`/`CriticalFeedbackEvent`; o response HTTP atual nao inclui o header documentado no OpenAPI.
- Respostas de erro customizadas do OpenAPI ainda nao foram implementadas; Quarkus/Bean Validation responde com o comportamento padrao.

## Persistencia e Integracoes

Persistencia modelada no Terraform:

- Tabela DynamoDB `feedbacks-<environment>` com billing `PAY_PER_REQUEST`.
- Chave primaria `id` string.
- GSI `dataEnvio-index` com partition key `periodo` e sort key `dataEnvio`.
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
- SES de relatorio semanal ainda e `NoOpReportEmailGateway` com log.
- `weekly-report` ainda nao consulta DynamoDB nem calcula metricas.

## Observabilidade e Operacao

Infraestrutura modelada:

- Log group por Lambda com retencao configuravel pelo modulo Lambda.
- Alarmes CloudWatch para `Errors` e `Throttles` de cada Lambda.
- Alarmes para metricas customizadas `NotificationFailureCount` e `WeeklyReportFailureCount` no namespace `FeedbackPlatform`.
- Dashboard `feedback-platform-<environment>` com metricas Lambda e metricas de negocio esperadas.

Codigo atual:

- Adapters no-op usam `org.jboss.logging.Logger`.
- Nao ha logs estruturados JSON configurados.
- Nao ha publicacao de metricas customizadas `FeedbackPlatform`.
- Nao ha mappers customizados de erro HTTP.

## Testes Visiveis

- `UrgenciaClassifierTest`: limites da classificacao e rejeicao de notas fora de 0..10.
- `FeedbackTest`: criacao, normalizacao, validacao de descricao, periodo, urgencia e correlation id.
- `PeriodoIsoWeekTest`: semana ISO UTC e virada de ano.
- `CriticalFeedbackEventTest`: validacao e normalizacao do evento critico.
- `AvaliacaoResourceTest`: criacao minima via HTTP retornando `201` e urgencia `CRITICA`.
- `HealthResourceTest`: `GET /health`.
- `NotifyCriticalFeedbackUseCaseTest`: delegacao para gateway de e-mail.
- `GenerateWeeklyReportUseCaseTest`: delegacao para gateway de relatorio.
