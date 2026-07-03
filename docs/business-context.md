# Contexto de Negocio

## Objetivo do Sistema

A Plataforma de Feedback Educacional coleta avaliacoes de estudantes, classifica automaticamente a urgencia do feedback e apoia a administracao com notificacoes para casos criticos e relatorios semanais.

O repositorio atende ao Tech Challenge IV. A solucao demonstra uma arquitetura serverless com API publica, persistencia planejada em DynamoDB, notificacao assincrona planejada via SNS/SES e relatorio semanal planejado via EventBridge/SES.

## Atores

- Estudante ou cliente da API: envia avaliacao com `descricao` e `nota`.
- Administrador educacional: deve receber e-mails de feedback critico e relatorio semanal.
- Equipe de desenvolvimento/operacao: evolui os modulos Java, Terraform e contratos.
- Agente de codigo: usa esta documentacao para retomar o trabalho sem redescobrir estrutura e decisoes.

## Vocabulario do Dominio

- Avaliacao ou feedback: registro textual enviado por estudante sobre uma aula/experiencia.
- `descricao`: texto do feedback, entre 10 e 1000 caracteres no contrato atual.
- `nota`: inteiro de 0 a 10.
- Urgencia: classificacao derivada da nota, com valores `CRITICA`, `MEDIA` ou `BAIXA`.
- Feedback critico: avaliacao com urgencia `CRITICA`; deve acionar notificacao administrativa.
- `dataEnvio`: timestamp gerado pelo backend.
- `periodo`: semana ISO no formato `AAAA-Www`, planejada para agrupar relatorios semanais; ainda nao existe no modelo Java atual.
- `correlationId`: identificador de rastreio documentado no OpenAPI; ainda nao esta implementado no codigo.

## Regras de Negocio Verificaveis

Regras implementadas no codigo e/ou contrato OpenAPI:

- `POST /avaliacao` e o endpoint oficial de entrada; a forma acentuada do caminho nao deve ser usada.
- `descricao` e obrigatoria.
- `descricao` deve ter no minimo 10 e no maximo 1000 caracteres.
- `nota` e obrigatoria.
- `nota` deve estar entre 0 e 10.
- notas 0 a 3 geram urgencia `CRITICA`.
- notas 4 a 6 geram urgencia `MEDIA`.
- notas 7 a 10 geram urgencia `BAIXA`.
- `id` e gerado pelo backend como UUID.
- `dataEnvio` e gerada pelo backend usando relogio UTC.
- feedback `CRITICA` passa pelo port `CriticalFeedbackPublisher`.

Regras documentadas/planejadas, mas ainda incompletas no codigo:

- Todo feedback deve ser persistido em DynamoDB.
- Todo feedback `CRITICA` deve publicar evento SNS para `critical-notifier`.
- Notificacao critica deve enviar e-mail via SES para `ADMIN_EMAIL_TO`.
- Relatorio semanal deve consultar feedbacks da semana, calcular indicadores e enviar e-mail administrativo.
- `periodo` deve ser persistido para consulta eficiente do relatorio.
- Erros de API devem seguir o modelo OpenAPI com `code`, `message`, `correlationId` e `details`.
- `X-Correlation-Id` deve ser propagado entre API, eventos, logs e respostas.

## Jornada Principal: Registro de Feedback

Estado implementado hoje:

1. Cliente envia `POST /avaliacao` com `descricao` e `nota`.
2. API valida campos obrigatorios, tamanho da descricao e faixa da nota.
3. Sistema classifica urgencia pela nota.
4. Sistema cria `id` e `dataEnvio`.
5. Sistema salva o feedback em memoria.
6. Se a urgencia for `CRITICA`, sistema chama publisher critico no-op.
7. Cliente recebe `201` com `id`, `status`, `urgencia` e `dataEnvio`.

Jornada esperada quando as integracoes forem concluidas:

1. O feedback e salvo em DynamoDB.
2. Feedback critico publica evento SNS.
3. O fluxo de notificacao ocorre fora da latencia da API.

## Jornada Administrativa: Notificacao Critica

Objetivo de negocio: reduzir o tempo ate a administracao perceber feedbacks com notas de 0 a 3.

Estado atual:

- O use case `NotifyCriticalFeedbackUseCase` existe.
- O handler `CriticalNotifierHandler` aceita `feedbackId` e `correlationId` em input simples.
- O gateway de e-mail e no-op e apenas registra log.

Estado esperado:

- Receber evento de feedback critico vindo do SNS.
- Enviar e-mail operacional com identificador do feedback e contexto suficiente para acao administrativa.
- Evitar duplicidade em retries quando idempotencia for implementada.

## Jornada Administrativa: Relatorio Semanal

Objetivo de negocio: dar visibilidade recorrente de volume, notas e criticidade dos feedbacks.

Estado atual:

- Terraform agenda a Lambda semanalmente.
- O use case `GenerateWeeklyReportUseCase` existe.
- O handler aceita `periodo` em input simples.
- O gateway de relatorio e no-op e apenas registra log.

Estado esperado:

- Consultar feedbacks por `periodo`.
- Calcular media das notas.
- Calcular contagens por dia e por urgencia.
- Destacar feedbacks criticos.
- Enviar relatorio mesmo sem feedbacks, com contadores zerados, se essa decisao continuar valida.

## Restricoes de Negocio e Operacao

- Endpoint sem acento reduz risco de encoding em clientes, API Gateway e testes.
- O MVP academico nao implementa autenticacao; isso nao deve ser interpretado como suficiente para producao real.
- Producao deve usar CORS com origens explicitas; `*` e aceitavel apenas em `dev`.
- E-mails dependem de identidades SES verificadas, especialmente em sandbox.
- Descricoes de feedback podem conter texto livre; evitar logar descricoes completas ate haver politica clara de privacidade.
- Dados sensiveis nao devem ser incluidos em metricas, alarmes ou logs.

## Criterios de Aceite Visiveis

Ja cobertos por testes atuais:

- `GET /health` retorna `UP`.
- `POST /avaliacao` com payload valido retorna `201`.
- Nota 2 retorna urgencia `CRITICA` no teste HTTP.
- Classificacao de urgencia cobre limites 0, 3, 4, 6, 7 e 10.
- Use cases de notifier/report delegam para seus gateways.

Ainda pendentes para cumprir a jornada completa:

- Persistencia real em DynamoDB.
- Publicacao real de SNS para criticos.
- Envio real de e-mail via SES.
- Relatorio semanal com consulta, calculo e envio.
- Propagacao de correlation id.
- Erros padronizados conforme OpenAPI.
- Testes de integracao com servicos AWS emulados.

## Perguntas Abertas

- Qual dominio real sera permitido no CORS de producao?
- Quais enderecos reais serao usados para `EMAIL_FROM` e `ADMIN_EMAIL_TO`?
- O endpoint publico permanecera sem autenticacao fora do contexto academico?
- Qual politica de privacidade/retencao se aplica ao texto livre de `descricao`?
- O relatorio semanal deve considerar semana ISO fechada em UTC ou horario de Sao Paulo?
- Qual conteudo minimo definitivo deve compor o e-mail critico e o relatorio semanal?
