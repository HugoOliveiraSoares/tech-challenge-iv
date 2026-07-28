## Why

Several documents under `docs/` describe an older runtime state where `feedback-api` and `critical-notifier` still use in-memory/no-op adapters, while the current code has DynamoDB, SNS, SES, SNS envelope handling, idempotency, and integration tests. This makes the project documentation unreliable for reviewers, maintainers, and future implementation work.

## What Changes

- Update documentation under `docs/` to reflect the current Java runtime, Terraform, tests, local workflows, and known remaining gaps.
- Keep `docs/Especificacao_Tecnica.md` unchanged and treat it as the functional reference for expected architecture and requirements.
- Remove or revise stale references to in-memory/no-op runtime behavior where concrete adapters are now active.
- Correct references to missing or stale documentation links, especially `docs/pendencias.md` references.
- Clarify the distinction between implemented code paths, validated tests, local fakecloud/E2E coverage, and remaining operational risks.
- Preserve the public API contract and avoid changing application code or infrastructure behavior.

## Capabilities

### New Capabilities

- `documentation-runtime-alignment`: Defines expectations for keeping derived documentation aligned with the current repository implementation while preserving the technical specification as the reference document.

### Modified Capabilities

No existing OpenSpec capability requirements are changed.

## Impact

- Affected docs: `docs/business-context.md`, `docs/decisions-and-tradeoffs.md`, `docs/development-environment.md`, `docs/software-architecture-and-patterns.md`, `docs/technical-context.md`, `docs/openapi-feedback-api.yaml`, and possibly `docs/diagrams/*.mmd`.
- Explicitly out of scope: `docs/Especificacao_Tecnica.md`, Java code, Terraform behavior, OpenAPI route semantics, tests, and generated binary docs such as PDFs or presentations.
- Review impact: documentation readers should be able to distinguish current implementation, validated coverage, and known gaps without relying on outdated status summaries.
