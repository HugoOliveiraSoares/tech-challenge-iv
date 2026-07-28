SHELL := /bin/bash
.DEFAULT_GOAL := help

# Configuration
AWS_REGION ?= us-east-1
AWS_ENDPOINT_URL ?= http://localhost:4566
AWS_ACCESS_KEY_ID ?= test
AWS_SECRET_ACCESS_KEY ?= test
DEV_ADMIN_EMAIL_TO ?= admin@example.com
DEV_EMAIL_FROM ?= no-reply@example.com
DEV_TERRAFORM_DIR := infra/environments/dev
DEV_TERRAFORM_VARS = \
	-var="admin_email_to=$(DEV_ADMIN_EMAIL_TO)" \
	-var="email_from=$(DEV_EMAIL_FROM)"
FEEDBACK_TABLE_NAME ?= feedbacks-dev
FEEDBACK_API_LAMBDA_NAME ?= feedback-api-dev
WEEKLY_REPORT_LAMBDA_NAME ?= weekly-report-dev
WEEKLY_REPORT_PERIODO ?= 2026-W30
SEED_FEEDBACK_PERIODO ?= $(WEEKLY_REPORT_PERIODO)
LAMBDA_OUTPUT_DIR ?= /tmp
CRITICAL_TOPIC_ARN ?= arn:aws:sns:$(AWS_REGION):000000000000:feedback-critical-topic-dev

MAVEN := ./mvnw -B
TERRAFORM_DEV := terraform -chdir="$(DEV_TERRAFORM_DIR)"
LOCAL_AWS_ENV = \
	AWS_ACCESS_KEY_ID="$(AWS_ACCESS_KEY_ID)" \
	AWS_SECRET_ACCESS_KEY="$(AWS_SECRET_ACCESS_KEY)" \
	AWS_REGION="$(AWS_REGION)" \
	AWS_ENDPOINT_URL="$(AWS_ENDPOINT_URL)"
AWS_LOCAL = $(LOCAL_AWS_ENV) aws --endpoint-url="$(AWS_ENDPOINT_URL)"

TEST_APPS := feedback-api critical-notifier weekly-report
TEST_APP_TARGETS := $(addprefix test-,$(TEST_APPS))

# Help
.PHONY: help env
help:
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make <target>\n\nTargets:\n"} /^[a-zA-Z0-9_-]+:.*##/ {printf "  %-24s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

env: ## Print local AWS environment exports.
	@./scripts/local-env.sh

# Local services
.PHONY: fakecloud-up fakecloud-down fakecloud-logs
fakecloud-up: ## Start fakecloud with Docker Compose.
	docker compose up -d fakecloud

fakecloud-down: ## Stop fakecloud containers.
	docker compose down

fakecloud-logs: ## Follow fakecloud logs.
	docker compose logs -f fakecloud

# Tests and build
.PHONY: test $(TEST_APP_TARGETS) test-it package openapi-validate
test: ## Run unit tests for all Maven modules.
	$(MAVEN) test

test-feedback-api: ## Run feedback-api tests and required modules.

test-critical-notifier: ## Run critical-notifier tests and required modules.

test-weekly-report: ## Run weekly-report tests and required modules.

$(TEST_APP_TARGETS):
	$(MAVEN) -pl apps/$(patsubst test-%,%,$@) -am test

test-it: ## Run integration-test lifecycle via Testcontainers (Docker required, no fakecloud-up needed).
	$(MAVEN) verify -Pintegration-test

package: ## Build Lambda artifacts expected by Terraform.
	$(MAVEN) clean package

openapi-validate: ## Validate the OpenAPI contract.
	npx --yes @apidevtools/swagger-cli@4.0.4 validate docs/openapi-feedback-api.yaml

# Terraform
.PHONY: terraform-fmt terraform-dev-init terraform-dev-validate
.PHONY: terraform-dev-plan terraform-dev-apply terraform-dev-destroy
terraform-fmt: ## Check Terraform formatting.
	terraform fmt -check -recursive infra

terraform-dev-init: fakecloud-up ## Initialize Terraform dev against fakecloud.
	$(TERRAFORM_DEV) init -backend=false

terraform-dev-validate: package terraform-dev-init ## Validate Terraform dev using real Lambda artifacts.
	$(TERRAFORM_DEV) validate

terraform-dev-plan: package terraform-dev-init ## Plan Terraform dev against fakecloud.
	$(TERRAFORM_DEV) plan $(DEV_TERRAFORM_VARS)

terraform-dev-apply: package terraform-dev-init ## Apply Terraform dev against fakecloud.
	$(TERRAFORM_DEV) apply -auto-approve $(DEV_TERRAFORM_VARS)

terraform-dev-destroy: terraform-dev-init ## Destroy Terraform dev resources from fakecloud.
	$(TERRAFORM_DEV) destroy -auto-approve $(DEV_TERRAFORM_VARS)

# Lambda invocation
.PHONY: invoke-feedback-api invoke-weekly-report invoke-lambdas
invoke-feedback-api: ## Invoke feedback-api Lambda through fakecloud.
	$(AWS_LOCAL) lambda invoke \
		--function-name $(FEEDBACK_API_LAMBDA_NAME) \
		--cli-binary-format raw-in-base64-out \
		--payload '{"version":"2.0","routeKey":"POST /avaliacao","rawPath":"/avaliacao","headers":{"content-type":"application/json","x-correlation-id":"test-123456"},"requestContext":{"http":{"method":"POST","path":"/avaliacao","sourceIp":"127.0.0.1","userAgent":"aws-cli"}},"body":"{\"descricao\":\"A aula estava confusa e nao consegui acompanhar o conteudo.\",\"nota\":2}","isBase64Encoded":false}' \
		$(LAMBDA_OUTPUT_DIR)/feedback-api-output.json
	@cat $(LAMBDA_OUTPUT_DIR)/feedback-api-output.json

invoke-weekly-report: ## Invoke weekly-report Lambda through fakecloud.
	$(AWS_LOCAL) lambda invoke \
		--function-name $(WEEKLY_REPORT_LAMBDA_NAME) \
		--cli-binary-format raw-in-base64-out \
		--payload '{"periodo":"$(WEEKLY_REPORT_PERIODO)"}' \
		$(LAMBDA_OUTPUT_DIR)/weekly-report-output.json
	@cat $(LAMBDA_OUTPUT_DIR)/weekly-report-output.json

invoke-lambdas: invoke-feedback-api invoke-weekly-report ## Invoke feedback-api and weekly-report Lambdas through fakecloud.

# Local workflows
.PHONY: seed-feedbacks-dev dev local-up local-down smoke verify
seed-feedbacks-dev: ## Populate feedbacks-dev with sample records for weekly-report tests.
	$(LOCAL_AWS_ENV) \
		FEEDBACK_TABLE_NAME="$(FEEDBACK_TABLE_NAME)" \
		SEED_FEEDBACK_PERIODO="$(SEED_FEEDBACK_PERIODO)" \
		./scripts/seed-feedbacks-dev.sh

dev: fakecloud-up ## Run feedback-api in Quarkus dev mode with local AWS env vars.
	$(LOCAL_AWS_ENV) \
		FEEDBACK_TABLE_NAME="$(FEEDBACK_TABLE_NAME)" \
		CRITICAL_TOPIC_ARN="$(CRITICAL_TOPIC_ARN)" \
		./mvnw -pl apps/feedback-api -am quarkus:dev

local-up: terraform-dev-apply ## Build, start fakecloud and provision the local dev stack.

local-down: terraform-dev-destroy fakecloud-down ## Destroy local dev stack and stop fakecloud.

smoke: ## Run a local smoke test against Terraform output or localhost:8080.
	./scripts/smoke-local.sh

e2e: local-up ## Run E2E validation against the persistent fakecloud and Terraform stack.
	./scripts/e2e-local.sh

verify: test package openapi-validate terraform-fmt terraform-dev-validate ## Run the main local verification suite.
