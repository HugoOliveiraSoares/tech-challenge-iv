# Contexto de Negocio

## Objetivo do Sistema

A Plataforma de Feedback Educacional coleta avaliacoes de estudantes, classifica automaticamente a urgencia de cada feedback e apoia a administracao com notificacoes para casos criticos e relatorios semanais.

O repositorio atende ao Tech Challenge IV. A solucao demonstra uma arquitetura serverless com API publica, persistencia planejada em DynamoDB, notificacao assincrona planejada via SNS/SES e relatorio semanal planejado via EventBridge/SES.

## Atores

- Estudante ou cliente da API: envia avaliacao com `descricao` e `nota`.
- Administrador educacional: deve receber e-mails de feedback critico e relatorio semanal.
- Equipe de desenvolvimento/operacao: evolui os modulos Java, Terraform e contratos.
- Agente de codigo: usa a documentacao versionada para retomar trabalho com seguranca.

## Vocabulario do Dominio

- Avaliacao ou feedback: registro textual enviado por estudante sobre uma aula/experiencia.
- `descricao`: texto do feedback, validado entre 10 e 1000 caracteres.
- `nota`: inteiro de 0 a 10.
- Urgencia: classificacao derivada da nota, com valores `CRITICA`, `MEDIA` ou `BAIXA`.
- Feedback critico: avaliacao com urgencia `CRITICA`; deve acionar notificacao administrativa.
- `dataEnvio`: timestamp gerado pelo backend.
- `periodo`: semana ISO UTC no formato `AAAA-Www`, usada para agrupar relatorios semanais.
- `correlationId`: identificador opcional de rastreio recebido via `X-Correlation-Id` e propagado no dominio/evento critico.

## Regras de Negocio Verificaveis

Implementadas no codigo atual:

- `POST /avaliacao` e o endpoint oficial de entrada; a forma acentuada do caminho nao deve ser usada.
- `descricao` e obrigatoria, trimada, com minimo 10 e maximo 1000 caracteres.
- `nota` e obrigatoria e deve estar entre 0 e 10.
- Notas 0 a 3 geram urgencia `CRITICA`.
- Notas 4 a 6 geram urgencia `MEDIA`.
- Notas 7 a 10 geram urgencia `BAIXA`.
- `id` e gerado pelo backend como UUID.
- `dataEnvio` e gerada pelo backend a partir de `Clock`.
- `periodo` e calculado por semana ISO em UTC.
- `correlationId` em branco vira `null`; quando informado, e trimado.
- Feedback `CRITICA` passa pelo port `CriticalFeedbackPublisher`.

Planejadas/modeladas, mas ainda incompletas no runtime Java:

- Todo feedback deve ser persistido em DynamoDB.
- Todo feedback `CRITICA` deve publicar evento SNS para `critical-notifier`.
- Notificacao critica deve enviar e-mail via SES para `ADMIN_EMAIL_TO`.
- Relatorio semanal deve consultar feedbacks da semana, calcular indicadores e enviar e-mail administrativo.
- Erros de API devem seguir o modelo OpenAPI com `code`, `message`, `correlationId` e `details`.
- `X-Correlation-Id` deve ser retornado no response e aparecer em logs/erros conforme contrato.

## Jornada Principal: Registro de Feedback

Estado implementado hoje:

1. Cliente envia `POST /avaliacao` com `descricao` e `nota`, opcionalmente `X-Correlation-Id`.
2. API valida campos obrigatorios, tamanho da descricao e faixa da nota.
3. Sistema classifica urgencia pela nota.
4. Sistema cria `id`, `dataEnvio` e `periodo`.
5. Sistema salva o feedback em memoria.
6. Se a urgencia for `CRITICA`, sistema chama publisher critico no-op.
7. Cliente recebe `201` com `id`, `status`, `urgencia` e `dataEnvio`.

Jornada esperada quando as integracoes forem concluidas:

1. Feedback e salvo em DynamoDB.
2. Feedback critico publica evento SNS.
3. Notificacao administrativa ocorre fora da latencia da API.

## Jornada Administrativa: Notificacao Critica

Objetivo de negocio: reduzir o tempo ate a administracao perceber feedbacks com notas de 0 a 3.

Estado atual:

- `NotifyCriticalFeedbackUseCase` existe.
- `CriticalNotifierHandler` aceita input simples com `feedbackId` e `correlationId`.
- Gateway de e-mail e no-op e apenas registra log.

Estado esperado:

- Receber evento de feedback critico vindo do SNS.
- Enviar e-mail operacional com identificador do feedback e contexto suficiente para acao administrativa.
- Evitar duplicidade em retries quando idempotencia for implementada.

## Jornada Administrativa: Relatorio Semanal

Objetivo de negocio: dar visibilidade recorrente de volume, notas e criticidade dos feedbacks.

Estado atual:

- Terraform agenda a Lambda semanalmente.
- `GenerateWeeklyReportUseCase` existe.
- Handler aceita `periodo` em input simples.
- Gateway de relatorio e no-op e apenas registra log.

Estado esperado:

- Consultar feedbacks por `periodo`.
- Calcular media das notas.
- Calcular volume por periodo e possivelmente por dia.
- Contabilizar feedbacks por urgencia.
- Destacar feedbacks criticos.
- Enviar relatorio administrativo por e-mail.

## Restricoes de Negocio e Operacao

- Endpoint sem acento reduz risco de encoding em clientes, API Gateway e testes.
- O MVP academico nao implementa autenticacao; isso nao deve ser interpretado como suficiente para producao real.
- Producao deve usar CORS com origens explicitas; `*` e aceitavel apenas em `dev`.
- E-mails dependem de identidades SES verificadas, especialmente em sandbox.
- `descricao` e texto livre e pode conter dados pessoais; evitar logar descricoes completas ate haver politica clara de privacidade.
- Dados sensiveis nao devem ser incluidos em metricas, alarmes ou logs.

## Criterios de Aceite Visiveis

Cobertos por testes atuais:

- `GET /health` retorna `UP`.
- `POST /avaliacao` com payload valido retorna `201`.
- Nota 2 retorna urgencia `CRITICA` no teste HTTP.
- Classificacao de urgencia cobre limites 0, 3, 4, 6, 7 e 10.
- `Feedback` calcula `periodo`, normaliza descricao/correlation id e rejeita invariantes invalidas.
- `PeriodoIsoWeek` cobre virada de ano ISO.
- Use cases de notifier/report delegam para seus gateways.
