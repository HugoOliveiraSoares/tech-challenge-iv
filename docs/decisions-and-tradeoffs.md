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

Consequencia: facilita manter adapters AWS SDK fora dos casos de uso. O custo e manter interfaces/records mesmo em fluxos pequenos. CDI e logging ainda aparecem em `core`, portanto nao ha isolamento completo de frameworks.

### Shared kernel para dominio transversal

Decisao: centralizar em `libs/shared-kernel` conceitos usados por mais de um app.

Evidencia: `Feedback`, `CriticalFeedbackEvent`, `PeriodoIsoWeek`, `UrgenciaClassifier`, `FeedbackRepository` e `CriticalFeedbackPublisher` vivem em `shared-kernel`; os apps dependem desse modulo.

Tradeoff: evita duplicacao de regra de dominio e contrato interno, mas aumenta risco de acoplamento se DTOs de transporte forem movidos para la sem estabilidade.

### Adapters AWS ativos atras de ports

Decisao: manter DynamoDB, SNS e SES em adapters de infraestrutura, atras de ports/use cases, sem levar SDK AWS para a regra de aplicacao.

Evidencia: `DynamoDbFeedbackRepository`, `SnsCriticalFeedbackPublisher`, `DynamoDbCriticalNotificationIdempotencyGateway`, `SesCriticalEmailGateway`, `DynamoDbWeeklyFeedbackReader`, `DynamoDbWeeklyReportIdempotencyGateway` e `SesReportEmailGateway`.

Tradeoff: aproxima o runtime da arquitetura Terraform e permite testes unitarios/integracao dos adapters, mas aumenta dependencia de configuracao correta de endpoints, credenciais locais e permissoes IAM. O `InMemoryFeedbackGateway` permanece no codigo sem CDI ativo e deve ser tratado como legado/test double, nao como runtime principal.

### weekly-report com AWS SDK direto em adapters

Decisao: manter o relatorio semanal com adapters reais para DynamoDB e SES, consumindo a mesma tabela de feedbacks gravada pela API.

Evidencia: `DynamoDbWeeklyFeedbackReader`, `DynamoDbWeeklyReportIdempotencyGateway`, `SesReportEmailGateway` e `AwsClientProducer` em `apps/weekly-report`.

Tradeoff: antecipa a parte operacional do relatorio e valida o desenho de tabela/GSI. Como a API ja grava no DynamoDB, o relatorio pode consumir feedbacks reais do mesmo armazenamento; scripts de seed continuam uteis para cenarios deterministas e demonstracoes locais.

### DynamoDB com GSI por periodo

Decisao: tabela `feedbacks` usa `id` como chave primaria e GSI `dataEnvio-index` com `periodo` e `dataEnvio`.

Evidencia: `infra/modules/dynamodb/main.tf` e `Feedback.periodo` calculado por `PeriodoIsoWeek`.

Tradeoff: favorece relatorio semanal por `Query`, mas obriga a aplicacao a persistir `periodo` corretamente e manter convencao de semana ISO UTC.

### Notificacao critica assincrona

Decisao: feedback critico deve sair da API via SNS e ser tratado por `critical-notifier`.

Evidencia: Terraform assina a Lambda no topico SNS e `CriarAvaliacaoUseCase` chama `CriticalFeedbackPublisher` apenas para `CRITICA`.

Tradeoff: reduz latencia e acoplamento da API, mas introduz entrega eventual, retries e necessidade de idempotencia no consumidor.

### Idempotencia conservadora para notificacao critica

Decisao: bloquear retries automaticos em estados ambiguos, envio em progresso ou falha permanente para priorizar prevencao de duplicidade.

Evidencia: `NotifyCriticalFeedbackUseCase` e `DynamoDbCriticalNotificationIdempotencyGateway`.

Tradeoff: reduz risco de e-mails duplicados em retries SNS/Lambda, mas pode exigir reconciliacao manual de registros `PROCESSING`, falhas ambiguas ou falhas permanentes.

### Terraform modular como fonte de infraestrutura

Decisao: provisionar ambientes com Terraform modularizado.

Evidencia: `infra/environments/{dev,prod}` e `infra/modules/*`.

Consequencia: infraestrutura e revisavel e reproduzivel. `plan/apply` completo depende dos zips Lambda existirem porque o modulo usa `filebase64sha256`.

### fakecloud para desenvolvimento local de infraestrutura

Decisao: `infra/environments/dev/` e somente para execucao local com fakecloud; `dev` usa endpoints AWS locais em `localhost:4566` e credenciais `test`. O modulo compartilhado do API Gateway usa o stage `$default` para nao incluir o nome do ambiente nas rotas encaminhadas ao Quarkus.

Evidencia: `docker-compose.yml`, `Makefile`, `infra/modules/api-gateway`, comentarios em `infra/environments/dev/main.tf` e endpoints em `infra/environments/dev/versions.tf`.

Tradeoff: reduz dependencia de conta AWS real, mas pode divergir da AWS em API Gateway, Lambda, EventBridge e CloudWatch. Qualquer validacao/deploy em AWS real deve usar `infra/environments/prod/`, nao reaproveitar `dev`.

### CI sem deploy

Decisao: o workflow `.github/workflows/ci.yml` valida build/testes, package, OpenAPI e Terraform, mas nao faz deploy.

Evidencia: jobs `java-test`, `java-package`, `openapi-validate` e `terraform-validate`.

Tradeoff: da feedback rapido e nao exige secrets AWS, mas nao valida `terraform apply`, fakecloud, AWS real ou integracoes end-to-end.

### CORS restrito por padrao em producao

Decisao: `prod` tem `cors_allowed_origins = []`; `dev` usa `[*]`.

Evidencia: `infra/environments/prod/variables.tf` e `infra/environments/dev/variables.tf`.

Consequencia: uso por browsers em producao exige decisao explicita sobre dominios. Evita copiar o comportamento permissivo de dev.

### EventBridge Scheduler para relatorio semanal

Decisao atual: usar `aws_scheduler_schedule` para acionar a Lambda `weekly-report` com `cron(59 23 ? * SUN *)` em UTC.

Evidencia: `infra/environments/*/variables.tf` e `infra/modules/eventbridge/main.tf`.

Tradeoff: UTC preserva a mesma convencao usada para persistir `Feedback.periodo` e para agrupar o relatorio. O Scheduler usa role propria com permissao restrita para invocar apenas a Lambda de relatorio. A configuracao nao define payload de entrada; o contrato Scheduler/handler ainda precisa de validacao integrada.

## Limitacoes Atuais

- O runtime implementa o caminho API -> DynamoDB -> SNS -> notifier, mas a validacao local automatizada ainda nao comprova todo o pipeline em um unico teste persistente.
- `make smoke` cobre somente o contrato HTTP basico; `make test-it` cobre integracoes fakecloud selecionadas via Testcontainers.
- Contrato efetivo do EventBridge Scheduler, separacao de topicos operacionais e recuperacao manual de estados idempotentes ainda exigem decisoes operacionais explicitas.
- Alarmes, DLQ, metricas customizadas e politica de dados pessoais continuam como lacunas relevantes para producao real.

## Riscos Aceitos ou Implicitos

- Endpoint sem autenticacao e aceitavel no escopo academico, mas insuficiente para producao real.
- `weekly-report` depende do GSI `dataEnvio-index`; se o indice, o atributo `periodo` ou o formato ISO week divergirem, o relatorio pode ficar vazio mesmo com feedbacks na tabela.
- Descricoes sao texto livre e podem conter dados pessoais; sem politica de privacidade, e prudente evitar logs completos.
- SES sandbox pode bloquear envios para destinatarios nao verificados.
- fakecloud pode nao reproduzir todos os comportamentos e limites da AWS real.
- Tratar `infra/environments/dev/` como ambiente AWS real criaria risco de provisionar recursos com endpoints/credenciais locais incorretos; a separacao pretendida e `dev` local fakecloud e `prod` AWS real.
- A idempotencia da notificacao critica evita duplicidade, mas pode deixar registros exigindo reconciliacao manual quando ha falha ambigua, falha permanente ou processamento interrompido.
- CI usa placeholders para validar Terraform; isso nao comprova que os zips reais existem fora do job de package.

## Alternativas Implicitamente Rejeitadas

- Monolito Lambda unico: a estrutura atual separa responsabilidades em tres Lambdas.
- Envio de e-mail dentro da API: o desenho usa SNS e notifier separado.
- Rota acentuada: substituida por `/avaliacao` nos contratos e implementacao.
- CORS `*` em producao: `prod` usa lista vazia por padrao.
- Relatorio por varredura principal: o codigo e a policy IAM implementam apenas `Query` no GSI por `periodo`.

As acoes pendentes mais importantes devem ser mantidas perto dos documentos que as motivam ou em issues do repositorio, para evitar referencias locais quebradas.
