# Deploy Na AWS

Use `infra/environments/prod` para AWS real. O ambiente `dev` utiliza FakeCloud.

Pre-requisito: os arquivos `apps/*/target/function.zip` devem estar gerados.

## Autenticacao

```bash
aws login --profile feedback-prod

eval "$(aws configure export-credentials \
  --profile feedback-prod \
  --format env)"

aws sts get-caller-identity
```

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

## Verificacao Do SES

```bash
aws ses get-identity-verification-attributes \
  --region us-east-1 \
  --identities \
  no-reply@example.com \
  admin@example.com
```

Verifique se a conta SES ainda esta no sandbox:

```bash
aws sesv2 get-account --region us-east-1
```

## Renovacao Das Credenciais

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

## Destruicao

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

## Observacoes

- `AWS_REGION` nao deve ser configurada manualmente nas variaveis de ambiente das Lambdas.
- Credenciais exportadas por `aws login` sao temporarias.
- O ambiente `prod` ainda utiliza state Terraform local.
- Se um `apply` falhar parcialmente, corrija o erro, gere um novo plano e aplique novamente.
