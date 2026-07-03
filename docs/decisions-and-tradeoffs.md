# Decisoes e Tradeoffs

## Decisoes Verificadas

### API publica sem acento

Decisao: usar `POST /avaliacao`, nao a forma acentuada do caminho.

Evidencia: `docs/openapi-feedback-api.yaml`, `apps/feedback-api/src/main/java/.../AvaliacaoResource.java` e `infra/modules/api-gateway/main.tf`.

Consequencia: reduz problemas de encoding em clientes HTTP, API Gateway, testes e ferramentas CLI. A rota acentuada nao deve ser adicionada sem decisao explicita.

### Maven multi-modulo

Decisao: organizar o projeto como Maven agregador com `libs/shared-kernel` e tres apps em `apps/*`.

Evidencia: `pom.xml` raiz lista `libs/shared-kernel`, `apps/feedback-api`, `apps/critical-notifier` e `apps/weekly-report`.

Tradeoff: facilita build unificado e compartilhamento controlado, mas exige cuidado para `shared-kernel` nao virar deposito de DTOs acoplados a transporte.

### Java 21 e Quarkus 3.15.3

Decisao: usar Java 21 e Quarkus para Lambdas.

Evidencia: `mise.toml`, `pom.xml` raiz e `infra/modules/lambda/variables.tf`.

Consequencia: stack moderna e compativel com runtime `java21`, mas o empacotamento precisa continuar produzindo `target/function.zip` nos caminhos esperados pelo Terraform.

### Tres responsabilidades serverless separadas

Decisao: separar `feedback-api`, `critical-notifier` e `weekly-report`.

Evidencia: modulos Maven, diretorios `apps/*` e Terraform dos ambientes.

Tradeoff: melhora isolamento, permissao IAM e evolucao independente. Aumenta a necessidade de contratos claros entre componentes, especialmente evento SNS e payloads de agendamento.

### Clean/Hexagonal simples no codigo Java

Decisao: separar `core` e `infra`, com use cases dependendo de interfaces `Gateway`.

Evidencia: estrutura dos tres apps e testes de use case com gateways fake/in-memory.

Consequencia: facilita substituir adapters no-op por AWS SDK sem contaminar regras de negocio. O custo e mais arquivos/interfaces mesmo em casos pequenos.

### Adapters temporarios in-memory/no-op

Decisao: manter implementacao executavel inicial sem AWS SDK real nos apps.

Evidencia: `InMemoryFeedbackGateway`, `NoOpCriticalFeedbackPublisher`, `NoOpEmailGateway` e `NoOpReportEmailGateway`.

Tradeoff: permite testar fluxo de aplicacao e gerar artefatos Lambda cedo, mas pode mascarar lacunas de integracao. A documentacao deve deixar claro que persistencia, SNS e SES ainda nao funcionam de ponta a ponta.

### Urgencia no shared kernel

Decisao: centralizar `Urgencia` e `UrgenciaClassifier` em `libs/shared-kernel`.

Evidencia: dependencia de `feedback-api` no `shared-kernel` e testes `UrgenciaClassifierTest`.

Consequencia: regra de classificacao fica unica e testavel. Deve permanecer pequena; mover DTOs de API ou eventos para la aumentaria acoplamento entre Lambdas.

### DynamoDB com GSI por periodo

Decisao: tabela `feedbacks` usa `id` como chave primaria e GSI `dataEnvio-index` com `periodo` e `dataEnvio`.

Evidencia: `infra/modules/dynamodb/main.tf`.

Tradeoff: favorece relatorio semanal por `Query`, mas obriga a aplicacao a gerar e persistir `periodo`. O codigo atual ainda nao faz isso.

### Notificacao critica assincrona

Decisao: feedback critico deve sair da API via SNS e ser tratado por `critical-notifier`.

Evidencia: Terraform assina a Lambda no topico SNS e `CriarAvaliacaoUseCase` chama `CriticalFeedbackPublisher` apenas para `CRITICA`.

Tradeoff: reduz latencia e acoplamento da API, mas introduz entrega eventual, retries e risco de duplicidade se idempotencia nao for implementada.

### Terraform como fonte de infraestrutura

Decisao: provisionar ambientes com Terraform modularizado.

Evidencia: `infra/environments/{dev,prod}` e `infra/modules/*`.

Consequencia: infraestrutura e revisavel e reproduzivel. O plano completo depende dos zips Lambda existirem porque o modulo usa `filebase64sha256`.

### fakecloud para desenvolvimento local de infraestrutura

Decisao: `dev` usa endpoints AWS locais em `localhost:4566`.

Evidencia: `docker-compose.yml` e `infra/environments/dev/versions.tf`.

Tradeoff: reduz dependencia de conta AWS real, mas pode divergir da AWS em API Gateway, Lambda, EventBridge e CloudWatch.

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
- Relatorio semanal nao consulta DynamoDB, nao calcula metricas e nao envia e-mail real.
- O handler do notifier nao processa o formato real de `SNSEvent`.
- O handler do weekly report nao processa um evento real de EventBridge; recebe input proprio.
- `periodo` existe no Terraform/contrato conceitual, mas nao no record `Feedback`.
- `correlationId` e documentado no OpenAPI, mas nao implementado no codigo.
- Respostas de erro padronizadas do OpenAPI ainda nao foram implementadas.
- Alarmes/dashboard esperam metricas customizadas que a aplicacao ainda nao publica.
- Nao ha DLQ para fluxos assincronos.
- Nao ha CI/CD versionado em `.github/workflows`.

## Riscos Aceitos ou Implicitos

- Endpoint sem autenticacao e aceitavel no escopo academico, mas insuficiente para producao real.
- `weekly-report` tem permissao `dynamodb:Scan` como fallback; isso pode gerar custo/latencia se virar caminho principal.
- Descricoes sao texto livre e podem conter dados pessoais; sem politica de privacidade, e prudente evitar logs completos.
- SES sandbox pode bloquear envios para destinatarios nao verificados.
- fakecloud pode nao reproduzir todos os comportamentos e limites da AWS real.
- Sem idempotencia, retries de SNS/EventBridge/Lambda podem causar notificacoes ou relatorios duplicados quando os envios reais forem implementados.

## Alternativas Implicitamente Rejeitadas

- Monolito Lambda unico: a estrutura atual separa responsabilidades em tres Lambdas.
- Envio de e-mail dentro da API: o desenho usa SNS e notifier separado.
- Rota acentuada: substituida por `/avaliacao` nos contratos e implementacao.
- CORS `*` em producao: `prod` usa lista vazia por padrao.

## Pontos para Revisitar

- Definir o contrato real do evento de feedback critico publicado no SNS.
- Trocar `NoOpCriticalFeedbackPublisher` por adapter SNS real.
- Trocar `InMemoryFeedbackGateway` por adapter DynamoDB real.
- Implementar adapter SES real para notificacao critica e relatorio semanal.
- Definir estrategia de idempotencia para notificacoes criticas por `feedbackId` e relatorios por `periodo`.
- Decidir se havera tabela auxiliar de controle/idempotencia e modela-la no Terraform.
- Implementar `correlationId` de ponta a ponta ou ajustar o OpenAPI se a decisao mudar.
- Implementar metricas customizadas ou ajustar alarmes/dashboard para metricas realmente emitidas.
- Avaliar DLQ para SNS/Lambda e EventBridge/Lambda.
- Decidir autenticacao/API key/JWT/Cognito antes de qualquer uso produtivo real.
- Definir se o relatorio semanal segue UTC ou timezone de negocio.

## Perguntas Abertas

- Qual dominio real sera usado no CORS de producao?
- Quais e-mails reais serao verificados no SES para remetente e destinatario administrativo?
- Qual e o formato definitivo do evento SNS de feedback critico?
- O relatorio deve ser enviado mesmo sem feedbacks na semana?
- Qual politica de retencao, anonimizacao ou privacidade se aplica ao texto das avaliacoes?
- Qual nivel de autenticacao e exigido se o sistema sair do contexto academico?
