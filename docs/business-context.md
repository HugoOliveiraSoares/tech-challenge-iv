# Contexto de Negócio

## Objetivo do Sistema

A Plataforma de Feedback Educacional existe para coletar avaliações de estudantes, identificar rapidamente feedbacks críticos e consolidar dados semanais para acompanhamento administrativo.

O produto atende ao Tech Challenge IV e deve demonstrar uma solução serverless com API pública, classificação automática, persistência, notificação e relatório.

## Atores

- Estudante ou cliente da API: envia feedback com descrição e nota.
- Administrador educacional: recebe e-mails sobre feedbacks críticos e relatório semanal.
- Equipe de desenvolvimento: implementa e opera a solução com Java, Quarkus, AWS/fakecloud e Terraform.
- Agente de código: usa a documentação versionada e `tasks/` para retomar implementação sem reexplorar o projeto.

## Vocabulário do Domínio

- Avaliação ou feedback: registro enviado por estudante contendo `descricao` e `nota`.
- Nota: valor inteiro de 0 a 10 que representa percepção do estudante.
- Urgência: classificação derivada automaticamente da nota.
- Feedback crítico: avaliação com urgência `CRITICA`, que exige notificação administrativa.
- Período: semana ISO-8601 no formato `AAAA-Www`, derivada de `dataEnvio` para consulta do relatório semanal.
- Correlation ID: identificador de rastreamento entre API, logs, persistência e eventos.

## Regras de Negócio Verificáveis

Regras extraídas de `docs/Especificacao_Tecnica.md` e `docs/openapi-feedback-api.yaml`:

- `descricao` é obrigatória.
- `descricao` deve ter no mínimo 10 e no máximo 1000 caracteres.
- `nota` é obrigatória.
- `nota` deve ser número inteiro entre 0 e 10.
- notas 0, 1, 2 e 3 geram urgência `CRITICA`.
- notas 4, 5 e 6 geram urgência `MEDIA`.
- notas 7, 8, 9 e 10 geram urgência `BAIXA`.
- todo feedback `CRITICA` deve publicar evento SNS para notificação administrativa.
- `dataEnvio` deve ser gerada pelo backend, não pelo cliente.
- `id` deve ser gerado pelo backend em formato UUID.
- `periodo` deve ser gerado pelo backend a partir de `dataEnvio`.
- erros devem seguir resposta padronizada com `code`, `message`, `correlationId` e `details`.

## Jornada Principal: Registro de Feedback

1. Estudante envia `POST /avaliacao` com `descricao` e `nota`.
2. Sistema valida o payload.
3. Sistema classifica urgência automaticamente.
4. Sistema persiste o feedback.
5. Se a urgência for `CRITICA`, sistema dispara notificação assíncrona.
6. Cliente recebe `201` com os dados gerados.

## Jornada Administrativa: Notificação Crítica

1. Um feedback com nota de 0 a 3 é registrado.
2. Sistema publica evento de feedback crítico.
3. Lambda de notificação envia e-mail para o administrador.
4. E-mail deve conter ao menos descrição, urgência e data de envio.

## Jornada Administrativa: Relatório Semanal

1. Agendamento semanal aciona o processamento.
2. Sistema busca feedbacks da semana.
3. Sistema calcula média geral das notas.
4. Sistema calcula quantidade por dia e por urgência.
5. Sistema lista feedbacks resumidos e destaca críticos.
6. Administrador recebe o relatório por e-mail.

## Restrições de Negócio e Operação

- O endpoint oficial do projeto é `POST /avaliacao`, sem acento, para evitar problemas de encoding em URLs e integrações.
- Para o escopo atual do Tech Challenge, o endpoint pode permanecer público com throttling no API Gateway e CORS controlado; autenticação, API key, JWT ou Cognito ficam como recomendação para produção real.
- Produção deve restringir CORS a domínios autorizados; o padrão `[*]` é apenas para dev/local. Enquanto não houver domínio real definido, usar `https://feedback.example.com` como placeholder documental de produção.
- Dados sensíveis não devem aparecer em logs, métricas ou alarmes.
- Remetente e destinatários dependem de validação SES, especialmente em sandbox.
- Feedback crítico deve ser notificado por componente assíncrono, não diretamente pela API.
- Para o MVP acadêmico, a postura de privacidade é simples: orientar que `descricao` não contenha dados pessoais e evitar logar descrições completas. Política formal de retenção, anonimização, consentimento ou TTL fica fora do MVP.

## Critérios de Aceite de Negócio

- API aceita feedback válido e retorna `201`.
- API rejeita payload inválido com erro padronizado.
- Feedback é persistido.
- Feedback com nota 0 a 3 gera notificação crítica.
- Relatório semanal é executado automaticamente.
- Logs estruturados e alarmes básicos estão disponíveis.
- Deploy é reproduzível via Terraform.
- Contrato OpenAPI fica versionado no repositório.

## Recomendações para Dúvidas de Negócio

- Autenticação: manter fora do MVP acadêmico; para produção real, exigir algum controle de acesso além de CORS e throttling.
- CORS de produção: usar `https://feedback.example.com` como placeholder até existir domínio real; não usar `*`.
- E-mails críticos: usar tom objetivo e operacional, contendo no mínimo `feedbackId`, `descricao`, `urgencia`, `nota`, `dataEnvio` e `correlationId`.
- Relatório semanal: considerar a semana ISO fechada identificada por `periodo` no formato `AAAA-Www`; se a execução ocorrer domingo às 23h59, o relatório cobre a semana que está encerrando.
- Relatório sem feedbacks: enviar mesmo assim, com contadores zerados, para diferenciar ausência de dados de falha do job.
- Horário do relatório: UTC é aceitável no MVP, mantendo `cron(59 23 ? * SUN *)`; `America/Sao_Paulo` fica como melhoria futura se for exigido.
- Destinatário administrativo: usar placeholder por enquanto, por exemplo `admin-feedback@example.com`; futuramente substituir por caixa ou grupo administrativo real, compatível com SES.

## Perguntas Abertas Remanescentes

- Qual domínio real substituirá `https://feedback.example.com` em produção?
- Qual endereço ou grupo real substituirá `admin-feedback@example.com` em `prod`?
