# Decisões e Tradeoffs

## Decisões Verificadas

### Endpoint sem acento

Decisão: usar `POST /avaliacao`, não `/avaliação`.

Evidência: `docs/Especificacao_Tecnica.md`, `docs/openapi-feedback-api.yaml` e `infra/modules/api-gateway/main.tf` usam `/avaliacao`.

Consequência: evita problemas de encoding em API Gateway, clientes HTTP, testes e ferramentas de linha de comando. Qualquer implementação deve rejeitar ou não documentar a rota acentuada, salvo decisão explícita em contrário.

### Separação por três Lambdas

Decisão: dividir responsabilidades em `feedback-api`, `critical-notifier` e `weekly-report`.

Evidência: especificação, variáveis Terraform e módulos de ambiente.

Consequência: reduz acoplamento operacional e permite permissões IAM específicas. Também aumenta a necessidade de contrato claro para eventos SNS e de observabilidade entre componentes.

### Notificação crítica assíncrona

Decisão: `feedback-api` publica SNS e não envia e-mail diretamente.

Evidência: RN007 e Terraform com assinatura SNS para `critical-notifier`.

Tradeoff: melhora isolamento e latência da API, mas introduz retries, entrega eventual e risco de e-mail duplicado sem idempotência.

### DynamoDB com GSI por período

Decisão: tabela DynamoDB tem `id` como chave primária e GSI `dataEnvio-index` com `periodo` e `dataEnvio`.

Evidência: `infra/modules/dynamodb/main.tf`.

Tradeoff: exige gerar e persistir `periodo` corretamente, mas evita `Scan` para relatório semanal em cenários normais.

### Terraform como fonte de infraestrutura

Decisão: provisionamento em Terraform, com módulos reutilizáveis e ambientes `dev`/`prod`.

Evidência: `infra/environments/*` e `infra/modules/*`.

Consequência: deploy fica reproduzível, mas a aplicação precisa gerar artefatos nos caminhos esperados antes de `plan/apply` completo.

### fakecloud para desenvolvimento local

Decisão: usar fakecloud em `localhost:4566` no ambiente `dev`.

Evidência: `docker-compose.yml` e endpoints AWS em `infra/environments/dev/versions.tf`.

Tradeoff: permite desenvolvimento sem AWS real, mas pode haver diferença de suporte/comportamento para API Gateway, Lambda, CloudWatch e EventBridge em relação à AWS.

### Produção com CORS fechado por padrão

Decisão: `prod` usa `cors_allowed_origins = []`; `dev` usa `[*]`.

Evidência: `infra/environments/prod/variables.tf` e `infra/environments/dev/variables.tf`.

Consequência: produção exige decisão explícita sobre domínios autorizados antes do uso real por browser.

Decisão documental complementar: usar `https://feedback.example.com` como placeholder genérico até existir domínio real de produção.

### Escopo acadêmico com práticas essenciais

Decisão recomendada: priorizar simplicidade acadêmica sem abandonar práticas operacionais essenciais.

Evidência: o repositório ainda está na fase de especificação/Terraform e o backlog prevê implementação incremental. Não há aplicação, pipeline ou testes executáveis.

Consequência: implementar primeiro contrato HTTP, validação, persistência, SNS/SES, logs estruturados, Terraform e testes. Autenticação completa, DLQ avançada e auto-apply de infraestrutura ficam como melhorias, salvo exigência explícita.

### Endpoint público sem autenticação no MVP

Decisão recomendada: manter o endpoint público no escopo atual, protegido por throttling e CORS restrito em produção.

Tradeoff: reduz complexidade para o Tech Challenge, mas não é suficiente para produção real. Se o sistema for exposto fora do contexto acadêmico, adicionar API key, JWT ou Cognito antes de operar com usuários reais.

### Relatório semanal mesmo sem dados

Decisão recomendada: enviar o relatório semanal mesmo quando não houver feedbacks na semana.

Consequência: o e-mail com contadores zerados serve como evidência operacional de que o agendamento e a Lambda executaram; evita confundir ausência de dados com falha silenciosa.

### Idempotência do relatório semanal

Decisão recomendada: tratar o envio do relatório semanal como idempotente por `periodo` usando a tabela auxiliar DynamoDB `feedback-processing-control-<environment>`.

Tradeoff: evita e-mails duplicados em retry ou reprocessamento manual. Para o MVP, não haverá TTL para os registros de controle e execução manual com reenvio forçado fica fora do escopo.

### Idempotência de notificação crítica

Decisão recomendada: suprimir e-mails duplicados por `feedbackId` no `critical-notifier` usando a tabela auxiliar DynamoDB `feedback-processing-control-<environment>`.

Tradeoff: retries de SNS/Lambda deixam de gerar múltiplos e-mails para o mesmo feedback. Para o MVP, uma escrita condicional com chave de controle derivada de `feedbackId` é suficiente.

### Placeholders operacionais

Decisão recomendada: usar placeholders explícitos enquanto não houver valores reais de produção.

Valores documentais:

- CORS produção: `https://feedback.example.com`.
- `ADMIN_EMAIL_TO`: `admin-feedback@example.com`.

Consequência: a documentação fica executável para exemplos e revisão, mas esses valores devem ser substituídos antes de uso real.

### Cron UTC no MVP

Decisão: UTC é aceitável para o relatório semanal no MVP.

Evidência: `infra/environments/*/variables.tf` usa `cron(59 23 ? * SUN *)` e `infra/modules/eventbridge/main.tf` usa `aws_cloudwatch_event_rule`, sem timezone configurável.

Consequência: não migrar agora para EventBridge Scheduler. `America/Sao_Paulo` permanece melhoria futura apenas se virar requisito obrigatório.

### Privacidade simples no MVP

Decisão: adotar postura simples de privacidade no MVP acadêmico.

Consequência: orientar que `descricao` não contenha dados pessoais e evitar logar descrições completas. Política formal de retenção, anonimização, consentimento e TTL fica fora do MVP.

### OpenAPI como contrato oficial

Decisão recomendada: tratar `docs/openapi-feedback-api.yaml` como contrato oficial da API HTTP.

Consequência: DTOs, validações, testes de contrato e documentação devem seguir o OpenAPI. Gerar DTOs automaticamente é opcional; não deve ser obrigatório enquanto não houver build Java.

### Ambientes limitados a `dev` e `prod`

Decisão recomendada: manter apenas `dev` e `prod` por enquanto.

Consequência: evita expansão prematura de Terraform e pipeline. Novos ambientes só devem ser criados quando houver necessidade concreta, como homologação separada.

### Terraform com aprovação manual

Decisão recomendada: pipeline futuro deve gerar plano e exigir aprovação manual antes de `apply`, especialmente em `prod`.

Tradeoff: reduz risco operacional e custo de mudanças acidentais. Auto-apply pode ser aceito apenas em ambiente local/dev controlado se o fluxo justificar.

## Decisões Planejadas, Ainda Não Materializadas em Código

### Clean Architecture nos módulos Java

Planejamento: separar `core` e `infra`, mantendo domínio livre de AWS SDK e Quarkus.

Evidência: seção de arquitetura limpa da especificação.

Risco: ainda não há código para garantir a fronteira. A primeira implementação Maven/Quarkus deve criar essa estrutura com cuidado para não acoplar use cases diretamente aos SDKs.

### Shared kernel

Planejamento: criar `libs/shared-kernel`.

Evidência: especificação e backlog.

Tradeoff: reduz duplicação entre Lambdas, mas pode virar acoplamento excessivo se receber DTOs específicos de transporte. Deve conter apenas domínio compartilhado e tipos estáveis.

### Testes com Testcontainers/fakecloud

Planejamento: cobrir integrações AWS locais com Testcontainers/fakecloud, além de unitários e contrato REST.

Evidência: especificação e tasks.

Limitação: não há build nem testes versionados hoje.

## Limitações Atuais

- Não há aplicação Java executável.
- Não há pipeline CI/CD.
- Não há contrato versionado para payload SNS além da descrição textual.
- Não há DLQ configurada para falhas assíncronas.
- Há decisão de idempotência por `feedbackId` para notificação crítica via `feedback-processing-control-<environment>`, mas ela ainda não está implementada.
- Há decisão de idempotência por `periodo` para relatório semanal via `feedback-processing-control-<environment>`, mas ela ainda não está implementada.
- O módulo de agendamento usa `aws_cloudwatch_event_rule` com cron UTC; isso é aceito no MVP, mas não atende timezone configurável.
- O Terraform calcula hash de artefatos Lambda que ainda não existem.

## Riscos Aceitos ou Implícitos

- Endpoint público sem autenticação documentada pode depender apenas de throttling e CORS, o que não é controle suficiente para todos os cenários.
- `weekly-report` tem permissão de `Scan` como fallback; isso é aceitável para baixo volume acadêmico, mas não para crescimento sem revisão.
- Métricas de negócio customizadas são esperadas pelos alarmes/dashboard, mas dependem da aplicação publicá-las corretamente.
- SES em sandbox pode impedir envio para destinatários não verificados.
- fakecloud pode não reproduzir integralmente falhas e limites dos serviços AWS reais.

## Pontos para Revisitar

- Implementar schema do evento `FeedbackCritico` publicado no SNS com o payload mínimo documentado na arquitetura.
- Implementar tabela auxiliar `feedback-processing-control-<environment>` para idempotência.
- Implementar mecanismo de idempotência para `critical-notifier` por `feedbackId`.
- Implementar idempotência do relatório semanal por `periodo`, usando a semana ISO fechada como janela de referência.
- Avaliar troca de `aws_cloudwatch_event_rule` por EventBridge Scheduler somente se timezone `America/Sao_Paulo` virar requisito obrigatório.
- Definir autenticação ou API key para o endpoint público se o sistema sair do escopo acadêmico.
- Criar DLQ e alarmes para fluxos assíncronos.
- Definir nomes de pacotes Java definitivos, mantendo coerência com `br/com/fiap/{app}` sugerido.

## Perguntas Abertas Remanescentes

- Qual domínio real substituirá `https://feedback.example.com` em produção?
- Qual e-mail ou grupo real substituirá `admin-feedback@example.com` em `prod`?
- Quais serão os nomes de pacotes Java definitivos?
