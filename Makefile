SHELL := /bin/bash

.DEFAULT_GOAL := help

AWS_REGION ?= us-east-1
ADMIN_EMAIL_TO ?= admin@example.com
EMAIL_FROM ?= no-reply@example.com
MODULE ?=

.PHONY: help setup up wait down restart logs dev test test-module integration regression package openapi-validate infra-fmt infra-validate infra-bootstrap clean-local

help:
	@printf 'Comandos disponiveis:\n'
	@printf '  make setup              Instala ferramentas declaradas no mise, quando disponivel\n'
	@printf '  make up                 Sobe fakecloud local\n'
	@printf '  make wait               Aguarda fakecloud aceitar conexao local\n'
	@printf '  make down               Para fakecloud local\n'
	@printf '  make logs               Mostra logs do fakecloud\n'
	@printf '  make dev                Sobe fakecloud e roda feedback-api em modo dev\n'
	@printf '  make test               Roda testes unitarios/Quarkus rapidos\n'
	@printf '  make test-module MODULE=apps/feedback-api  Roda testes de um modulo Maven\n'
	@printf '  make integration        Roda testes de integracao (*IT)\n'
	@printf '  make regression         Roda validacao regressiva completa local\n'
	@printf '  make infra-bootstrap    Aplica Terraform dev contra fakecloud\n'
	@printf '  make clean-local        Remove estado local do fakecloud e artefatos temporarios\n'

setup:
	@if command -v mise >/dev/null 2>&1; then mise install; else printf 'mise nao encontrado; garanta Java 21 no ambiente.\n'; fi

up:
	docker compose up -d
	scripts/local/wait-fakecloud.sh

wait:
	scripts/local/wait-fakecloud.sh

down:
	docker compose down

restart:
	docker compose down
	docker compose up -d
	scripts/local/wait-fakecloud.sh

logs:
	docker compose logs -f fakecloud

dev: up
	./mvnw -pl apps/feedback-api -am quarkus:dev

test:
	./mvnw test

test-module:
	@if [ -z "$(MODULE)" ]; then printf 'Informe MODULE=apps/feedback-api, por exemplo.\n' >&2; exit 1; fi
	./mvnw -pl $(MODULE) -am test

integration: up
	./mvnw verify -Pintegration


regression: up openapi-validate infra-validate
	./mvnw clean verify -Pregression

package:
	./mvnw clean package

openapi-validate:
	npx --yes @apidevtools/swagger-cli@4.0.4 validate docs/openapi-feedback-api.yaml

infra-fmt:
	terraform fmt -check -recursive infra

infra-validate: infra-fmt
	scripts/local/ensure-lambda-placeholders.sh
	terraform -chdir=infra/environments/dev init -backend=false
	terraform -chdir=infra/environments/dev validate
	terraform -chdir=infra/environments/prod init -backend=false
	terraform -chdir=infra/environments/prod validate

infra-bootstrap: up package
	AWS_REGION=$(AWS_REGION) ADMIN_EMAIL_TO=$(ADMIN_EMAIL_TO) EMAIL_FROM=$(EMAIL_FROM) scripts/local/bootstrap-fakecloud.sh

clean-local:
	docker compose down -v
	rm -rf .fakecloud
	rm -f apps/feedback-api/target/function.zip apps/critical-notifier/target/function.zip apps/weekly-report/target/function.zip
