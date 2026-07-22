# Decisoes e Tradeoffs

## Decisoes Verificadas

### API publica sem acento

Decisao: usar `POST /avaliacao`, nao a forma acentuada do caminho.

Evidencia: `docs/openapi-feedback-api.yaml`, `AvaliacaoResource` e `infra/modules/api-gateway/main.tf`.

Consequencia: reduz problemas de encoding em clientes HTTP, API Gateway, testes e ferramentas CLI. A rota acentuada nao deve ser adicionada sem decisao explicita.

### Maven multi-modulo

Decisao: organizar o projeto como Maven agregador com `libs/shared-kernel` e tres apps em `apps/*`.

Evidencia: `pom.xml` raiz lista `libs/shared-kernel`, `apps/feedback-api`, `apps/critical-notifier` e `apps/weekly-report`.

Tradeoff: facilita build unificado e compartilhamento controlado, mas exige cuidado para `shared-kernel` nao virar deposito de DTOs de transporte.

### Java 21 e Quarkus 3.15.3

Decisao: usar Java 21 e Quarkus para a API e Lambdas.

Evidencia: `mise.toml`, `pom.xml` raiz, `apps/*/pom.xml` e `infra/modules/lambda/variables.tf`.

Consequencia: stack moderna e compativel com runtime `java21`; o empacotamento precisa continuar produzindo `target/function.zip` nos caminhos esperados pelo Terraform.

### Tres responsabilidades serverless separadas

Decisao: separar `feedback-api`, `critical-notifier` e `weekly-report`.

Evidencia: modulos Maven, diretorios `apps/*` e Terraform dos ambientes.

Tradeoff: melhora isolamento, permissoes IAM e evolucao independente. Aumenta a necessidade de contratos claros entre componentes, especialmente evento SNS e payload de agendamento.

### Clean/Hexagonal simples no codigo Java

Decisao: separar `core` e `infra`, com use cases dependendo de ports/interfaces.

Evidencia: estrutura dos tres apps, `FeedbackRepository`, `CriticalFeedbackPublisher`, gateways locais e testes de use case com doubles simples.

Consequencia: facilita substituir adapters no-op por AWS SDK sem contaminar regras de negocio. O custo e manter algumas interfaces/records mesmo em fluxos pequenos.

### Shared kernel para dominio transversal

Decisao: centralizar em `libs/shared-kernel` conceitos usados por mais de um app.

Evidencia: `Feedback`, `CriticalFeedbackEvent`, `PeriodoIsoWeek`, `UrgenciaClassifier`, `FeedbackRepository` e `CriticalFeedbackPublisher` vivem em `shared-kernel`; os apps dependem desse modulo.

Tradeoff: evita duplicacao de regra de dominio e contrato interno, mas aumenta risco de acoplamento se DTOs de transporte forem movidos para la sem estabilidade.

### Adapters temporarios in-memory/no-op

Decisao: manter implementacao executavel inicial sem AWS SDK real nos apps.

Evidencia: `InMemoryFeedbackGateway`, `NoOpCriticalFeedbackPublisher`, `NoOpEmailGateway` e `NoOpReportEmailGateway`.

Tradeoff: permite testar fluxo de aplicacao, CI e empacotamento cedo, mas pode mascarar lacunas de integracao. Persistencia, SNS e SES ainda nao funcionam de ponta a ponta.

### DynamoDB com GSI por periodo

Decisao: tabela `feedbacks` usa `id` como chave primaria e GSI `dataEnvio-index` com `periodo` e `dataEnvio`.

Evidencia: `infra/modules/dynamodb/main.tf` e `Feedback.periodo` calculado por `PeriodoIsoWeek`.

Tradeoff: favorece relatorio semanal por `Query`, mas obriga a aplicacao a persistir `periodo` corretamente e manter convencao de semana ISO UTC.

### Notificacao critica assincrona

Decisao: feedback critico deve sair da API via SNS e ser tratado por `critical-notifier`.

Evidencia: Terraform assina a Lambda no topico SNS e `CriarAvaliacaoUseCase` chama `CriticalFeedbackPublisher` apenas para `CRITICA`.

Tradeoff: reduz latencia e acoplamento da API, mas introduz entrega eventual, retries e risco de duplicidade se idempotencia nao for implementada.

### Terraform modular como fonte de infraestrutura

Decisao: provisionar ambientes com Terraform modularizado.

Evidencia: `infra/environments/{dev,prod}` e `infra/modules/*`.

Consequencia: infraestrutura e revisavel e reproduzivel. `plan/apply` completo depende dos zips Lambda existirem porque o modulo usa `filebase64sha256`.

### fakecloud para desenvolvimento local de infraestrutura

Decisao: `dev` usa endpoints AWS locais em `localhost:4566`.

Evidencia: `docker-compose.yml` e `infra/environments/dev/versions.tf`.

Tradeoff: reduz dependencia de conta AWS real, mas pode divergir da AWS em API Gateway, Lambda, EventBridge e CloudWatch.

### CI sem deploy

Decisao: o workflow `.github/workflows/ci.yml` valida build/testes, package, OpenAPI e Terraform, mas nao faz deploy.

Evidencia: jobs `java-test`, `java-package`, `openapi-validate` e `terraform-validate`.

Tradeoff: da feedback rapido e nao exige secrets AWS, mas nao valida `terraform apply`, fakecloud, AWS real ou integracoes end-to-end.

### CORS restrito por padrao em producao

Decisao: `prod` tem `cors_allowed_origins = []`; `dev` usa `[*]`.

Evidencia: `infra/environments/prod/variables.tf` e `infra/environments/dev/variables.tf`.

Consequencia: uso por browsers em producao exige decisao explicita sobre dominios. Evita copiar o comportamento permissivo de dev.

### Cron UTC para relatorio semanal

Decisao atual: usar `cron(59 23 ? * SUN *)` em `aws_cloudwatch_event_rule`.

Evidencia: `infra/environments/*/variables.tf` e `infra/modules/eventbridge/main.tf`.

Tradeoff: simples e suportado pelo recurso atual, mas nao configura timezone. Se o requisito for horario de Sao Paulo, deve-se revisar o modulo, possivelmente para EventBridge Scheduler.

## Limitacoes Atuais

- A API persiste apenas em memoria; nenhum item e gravado em DynamoDB pelo codigo Java atual.
- Feedback critico nao publica SNS real; o adapter apenas loga.
- Notificacao critica nao envia e-mail real; o adapter SES e no-op.
- Relatorio semanal ja consulta DynamoDB, calcula metricas e envia e-mail via SES, mas ainda nao tem teste de integracao contra fakecloud/AWS.
- O handler do notifier nao processa o formato real de `SNSEvent`.
- O handler do weekly report nao processa um evento real de EventBridge/CloudWatch Events; recebe input proprio.
- `X-Correlation-Id` e aceito, gerado quando ausente, propagado internamente e retornado no response HTTP.
- Respostas de erro padronizadas do OpenAPI foram implementadas para validacao, JSON invalido, regra de dominio e erro interno na feedback API.
- Alarmes/dashboard esperam metricas customizadas que a aplicacao ainda nao publica.
- Nao ha DLQ ou idempotencia para fluxos assincronos.

## Riscos Aceitos ou Implicitos

- Endpoint sem autenticacao e aceitavel no escopo academico, mas insuficiente para producao real.
- `weekly-report` tem permissao `dynamodb:Scan` como fallback; isso pode gerar custo/latencia se virar caminho principal.
- Descricoes sao texto livre e podem conter dados pessoais; sem politica de privacidade, e prudente evitar logs completos.
- SES sandbox pode bloquear envios para destinatarios nao verificados.
- fakecloud pode nao reproduzir todos os comportamentos e limites da AWS real.
- Sem idempotencia no fluxo de notificacao critica, retries de SNS/Lambda podem causar notificacoes duplicadas quando o envio real for implementado.
- CI usa placeholders para validar Terraform; isso nao comprova que os zips reais existem fora do job de package.

## Alternativas Implicitamente Rejeitadas

- Monolito Lambda unico: a estrutura atual separa responsabilidades em tres Lambdas.
- Envio de e-mail dentro da API: o desenho usa SNS e notifier separado.
- Rota acentuada: substituida por `/avaliacao` nos contratos e implementacao.
- CORS `*` em producao: `prod` usa lista vazia por padrao.
- Relatorio por varredura principal: a modelagem do GSI indica preferencia por `Query` por `periodo`, embora `Scan` esteja permitido como fallback.
