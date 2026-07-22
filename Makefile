SHELL := /bin/bash

AWS_REGION ?= us-east-1
AWS_ENDPOINT_URL ?= http://localhost:4566
AWS_ACCESS_KEY_ID ?= test
AWS_SECRET_ACCESS_KEY ?= test
DEV_ADMIN_EMAIL_TO ?= admin@example.com
DEV_EMAIL_FROM ?= no-reply@example.com
DEV_TERRAFORM_DIR := infra/environments/dev
DEV_TERRAFORM_VARS := -var="admin_email_to=$(DEV_ADMIN_EMAIL_TO)" -var="email_from=$(DEV_EMAIL_FROM)"

.PHONY: help
help:
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make <target>\n\nTargets:\n"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  %-24s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

.PHONY: env
env: ## Print local AWS environment exports.
	@./scripts/local-env.sh

.PHONY: fakecloud-up
fakecloud-up: ## Start fakecloud with Docker Compose.
	docker compose up -d fakecloud

.PHONY: fakecloud-down
fakecloud-down: ## Stop fakecloud containers.
	docker compose down

.PHONY: fakecloud-logs
fakecloud-logs: ## Follow fakecloud logs.
	docker compose logs -f fakecloud

.PHONY: test
test: ## Run unit tests for all Maven modules.
	./mvnw -B test

.PHONY: test-feedback-api
test-feedback-api: ## Run feedback-api tests and required modules.
	./mvnw -B -pl apps/feedback-api -am test

.PHONY: test-critical-notifier
test-critical-notifier: ## Run critical-notifier tests and required modules.
	./mvnw -B -pl apps/critical-notifier -am test

.PHONY: test-weekly-report
test-weekly-report: ## Run weekly-report tests and required modules.
	./mvnw -B -pl apps/weekly-report -am test

.PHONY: test-it
test-it: fakecloud-up ## Run integration-test lifecycle against local fakecloud when *IT tests exist.
	AWS_ACCESS_KEY_ID=$(AWS_ACCESS_KEY_ID) AWS_SECRET_ACCESS_KEY=$(AWS_SECRET_ACCESS_KEY) AWS_REGION=$(AWS_REGION) AWS_ENDPOINT_URL=$(AWS_ENDPOINT_URL) ./mvnw -B verify -Pintegration-test

.PHONY: package
package: ## Build Lambda artifacts expected by Terraform.
	./mvnw -B clean package

.PHONY: openapi-validate
openapi-validate: ## Validate the OpenAPI contract.
	npx --yes @apidevtools/swagger-cli@4.0.4 validate docs/openapi-feedback-api.yaml

.PHONY: terraform-fmt
terraform-fmt: ## Check Terraform formatting.
	terraform fmt -check -recursive infra

.PHONY: terraform-dev-init
terraform-dev-init: fakecloud-up ## Initialize Terraform dev against fakecloud.
	terraform -chdir=$(DEV_TERRAFORM_DIR) init -backend=false

.PHONY: terraform-dev-validate
terraform-dev-validate: package terraform-dev-init ## Validate Terraform dev using real Lambda artifacts.
	terraform -chdir=$(DEV_TERRAFORM_DIR) validate

.PHONY: terraform-dev-plan
terraform-dev-plan: package terraform-dev-init ## Plan Terraform dev against fakecloud.
	terraform -chdir=$(DEV_TERRAFORM_DIR) plan $(DEV_TERRAFORM_VARS)

.PHONY: terraform-dev-apply
terraform-dev-apply: package terraform-dev-init ## Apply Terraform dev against fakecloud.
	terraform -chdir=$(DEV_TERRAFORM_DIR) apply -auto-approve $(DEV_TERRAFORM_VARS)
	AWS_ACCESS_KEY_ID=$(AWS_ACCESS_KEY_ID) AWS_SECRET_ACCESS_KEY=$(AWS_SECRET_ACCESS_KEY) AWS_REGION=$(AWS_REGION) AWS_ENDPOINT_URL=$(AWS_ENDPOINT_URL) ./scripts/fakecloud-default-stage.sh

.PHONY: terraform-dev-destroy
terraform-dev-destroy: terraform-dev-init ## Destroy Terraform dev resources from fakecloud.
	terraform -chdir=$(DEV_TERRAFORM_DIR) destroy -auto-approve $(DEV_TERRAFORM_VARS)

.PHONY: dev
dev: fakecloud-up ## Run feedback-api in Quarkus dev mode with local AWS env vars.
	AWS_ACCESS_KEY_ID=$(AWS_ACCESS_KEY_ID) AWS_SECRET_ACCESS_KEY=$(AWS_SECRET_ACCESS_KEY) AWS_REGION=$(AWS_REGION) AWS_ENDPOINT_URL=$(AWS_ENDPOINT_URL) FEEDBACK_TABLE_NAME=feedbacks-dev CRITICAL_TOPIC_ARN=arn:aws:sns:$(AWS_REGION):000000000000:feedback-critical-topic-dev ./mvnw -pl apps/feedback-api -am quarkus:dev

.PHONY: local-up
local-up: terraform-dev-apply ## Build, start fakecloud and provision the local dev stack.

.PHONY: local-down
local-down: terraform-dev-destroy fakecloud-down ## Destroy local dev stack and stop fakecloud.

.PHONY: smoke
smoke: ## Run a local smoke test against Terraform output or localhost:8080.
	./scripts/smoke-local.sh

.PHONY: verify
verify: test package openapi-validate terraform-fmt terraform-dev-validate ## Run the main local verification suite.
