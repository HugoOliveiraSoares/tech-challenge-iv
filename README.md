# Tech Challenge IV

Este repositório contém a solução proposta para o Tech Challenge IV: uma plataforma serverless de feedback educacional para coletar avaliações de estudantes, classificar automaticamente a urgência de cada feedback e apoiar a atuação administrativa com notificações e relatórios.

A solução é orientada a eventos e combina uma API pública para registro de avaliações, persistência em DynamoDB, publicação de eventos para feedbacks críticos e processamento assíncrono para envio de e-mails administrativos. Também está previsto um relatório semanal consolidado com indicadores de notas, volume por período e destaques de feedbacks críticos.

## Visão Geral

- `feedback-api`: recebe `POST /avaliacao`, valida o payload, classifica a urgência, persiste o feedback e publica eventos críticos.
- `critical-notifier`: consome eventos de feedback crítico e envia notificação por e-mail via SES.
- `weekly-report`: executa de forma agendada, consolida os feedbacks da semana e envia relatório administrativo.
- `infra/`: define a infraestrutura AWS com Terraform para API Gateway, Lambda, DynamoDB, SNS, SES, EventBridge e CloudWatch.

O projeto usa Java 21, Quarkus, Terraform e serviços AWS, com suporte a execução local dos serviços cloud por meio do fakecloud.

## Estado Atual

Atualmente o repositório concentra a documentação técnica, o contrato OpenAPI, a modelagem Terraform e o backlog de implementação. A estrutura das aplicações Java/Quarkus ainda está em construção.

## Documentação

Os documentos de referência do projeto estão disponíveis em [`docs/`](docs/), incluindo a especificação técnica e o contrato OpenAPI da API de feedback.

## Board

O acompanhamento das atividades está no GitHub Projects:

[board](https://github.com/users/HugoOliveiraSoares/projects/4/views/1)
