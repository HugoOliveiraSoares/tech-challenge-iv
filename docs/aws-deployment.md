# Deploy Na AWS

Use `infra/environments/prod` para AWS real. O ambiente `dev` utiliza FakeCloud.

Este guia descreve o procedimento manual e nao afirma que a stack esteja aplicada atualmente. O state de `prod` e local; confirme conta, regiao, plano e custodia do state antes de qualquer operacao.

Pre-requisito: os arquivos `apps/*/target/function.zip` devem estar gerados.

## Autenticação

```bash
aws login --profile feedback-prod

eval "$(aws configure export-credentials \
  --profile feedback-prod \
  --format env)"

aws sts get-caller-identity
```

O profile `feedback-prod` precisa existir previamente na configuracao local da AWS CLI. Confirme que a identidade retornada pertence a conta autorizada antes de continuar.

Defina as variaveis obrigatorias sem versionar valores locais:

```bash
export TF_VAR_aws_region=us-east-1
export TF_VAR_admin_email_to=admin@example.com
export TF_VAR_email_from=no-reply@example.com
```

`infra/environments/prod/terraform.tfvars.example` lista tambem `environment` e `cors_allowed_origins`. Em uso real, substitua os e-mails de exemplo e configure origens CORS explicitas.

## Terraform Init

```bash
terraform -chdir=infra/environments/prod init
terraform -chdir=infra/environments/prod validate
```

## Terraform Plan

```bash
terraform -chdir=infra/environments/prod plan \
  -out=/tmp/feedback-platform-prod.tfplan
```

Revise o plano:

```bash
terraform -chdir=infra/environments/prod show \
  /tmp/feedback-platform-prod.tfplan
```

## Terraform Apply

```bash
terraform -chdir=infra/environments/prod apply \
  /tmp/feedback-platform-prod.tfplan
```

## Outputs

```bash
terraform -chdir=infra/environments/prod output

terraform -chdir=infra/environments/prod output -raw api_base_url
```

## Verificação Do SES

Use a mesma região informada a Terraform em `aws_region`:

```bash
aws ses get-identity-verification-attributes \
  --region "${TF_VAR_aws_region:-us-east-1}" \
  --identities \
  "$TF_VAR_email_from" \
  "$TF_VAR_admin_email_to"
```

Verifique se a conta SES ainda esta no sandbox:

```bash
aws sesv2 get-account --region "${TF_VAR_aws_region:-us-east-1}"
```

## Renovação Das Credenciais

Se ocorrer `ExpiredToken`:

```bash
unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
unset AWS_SESSION_TOKEN
unset AWS_SECURITY_TOKEN

aws login --profile feedback-prod

eval "$(aws configure export-credentials \
  --profile feedback-prod \
  --format env)"

aws sts get-caller-identity
```

Depois, gere um novo plano e execute o `apply`.

## Destruição

Revise antes de aplicar:

```bash
terraform -chdir=infra/environments/prod plan \
  -destroy \
  -out=/tmp/feedback-platform-prod-destroy.tfplan

terraform -chdir=infra/environments/prod show \
  /tmp/feedback-platform-prod-destroy.tfplan

terraform -chdir=infra/environments/prod apply \
  /tmp/feedback-platform-prod-destroy.tfplan
```

## Observações

- `AWS_REGION` nao deve ser configurada manualmente nas variaveis de ambiente das Lambdas.
- Credenciais exportadas por `aws login` sao temporarias.
- O ambiente `prod` ainda utiliza state Terraform local.
- Se um `apply` falhar parcialmente, corrija o erro, gere um novo plano e aplique novamente.
- O notificador critico tenta o envio pelo SES no maximo tres vezes e repete automaticamente somente quando o SES identifica throttling explicito.
- Respostas SES 5xx sem throttling e falhas de transporte, incluindo timeout e connection reset, ficam em `FAILED_AFTER_SEND_ATTEMPT` e exigem reconciliacao manual; apenas `FAILED_BEFORE_SEND` permite uma nova tentativa de entrega.
