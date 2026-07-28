## 1. Runtime Evidence Review

- [x] 1.1 Reconfirm active adapters and handlers in `apps/feedback-api`, `apps/critical-notifier`, and `apps/weekly-report` before editing docs.
- [x] 1.2 Reconfirm existing test coverage, especially current `*IT.java` classes and adapter/handler tests.
- [x] 1.3 Reconfirm Terraform, Makefile, scripts, and CI behavior relevant to local execution and validation.

## 2. Documentation Status Updates

- [x] 2.1 Update `docs/business-context.md` to describe current implemented journeys and remaining business/operational caveats accurately.
- [x] 2.2 Update `docs/software-architecture-and-patterns.md` to replace stale in-memory/no-op architecture descriptions with current DynamoDB, SNS, SES, SNS envelope, and idempotency behavior.
- [x] 2.3 Update `docs/technical-context.md` to reflect current module dependencies, active integrations, environment variables, observability status, and test coverage.
- [x] 2.4 Update `docs/development-environment.md` to align local workflow, integration test, E2E, and fakecloud caveats with the current implementation.
- [x] 2.5 Update `docs/decisions-and-tradeoffs.md` to revise decisions and risks that still assume missing adapters.
- [x] 2.6 Update `docs/openapi-feedback-api.yaml` description to remove stale in-memory/no-op claims while keeping the HTTP contract unchanged.
- [x] 2.7 Review `docs/diagrams/*.mmd` and update only diagrams that are inaccurate relative to the current or target flow.

## 3. Link and Drift Cleanup

- [x] 3.1 Search `docs/` for stale terms such as `NoOp`, `no-op`, `in-memory`, `memoria`, `em memoria`, `pendencias.md`, and update or justify any remaining occurrences.
- [x] 3.2 Remove or replace broken references to `pendencias.md` unless a valid existing target is available.
- [x] 3.3 Ensure all updated docs clearly distinguish target architecture, implemented behavior, tested coverage, and remaining gaps.
- [x] 3.4 Verify `docs/Especificacao_Tecnica.md` remains unchanged.

## 4. Verification

- [x] 4.1 Run `npx --yes @apidevtools/swagger-cli@4.0.4 validate docs/openapi-feedback-api.yaml` if `openapi-feedback-api.yaml` changes.
- [x] 4.2 Run `git diff -- docs openspec/changes/align-docs-with-current-runtime` and review for accidental code/Terraform changes.
- [x] 4.3 Run `openspec validate align-docs-with-current-runtime --strict` or the repository-supported OpenSpec validation command for this change.
