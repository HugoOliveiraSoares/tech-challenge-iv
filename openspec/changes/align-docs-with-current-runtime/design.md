## Context

The repository has moved beyond the documentation status described in several files under `docs/`. The current Java runtime includes active AWS-backed adapters for `feedback-api` persistence and critical event publishing, active SNS envelope processing in `critical-notifier`, SES delivery for critical notifications, DynamoDB-backed idempotency, and integration tests for notifier/report paths. Some derived documents still describe `feedback-api` and `critical-notifier` as in-memory/no-op and refer to pending work that is now present in code.

`docs/Especificacao_Tecnica.md` remains the functional reference and should not be edited as part of this change. The remaining documentation should be reconciled with the current code while preserving useful distinctions between target architecture, implemented behavior, tested behavior, and known risks.

## Goals / Non-Goals

**Goals:**

- Align derived docs with current source, tests, Terraform, scripts, and CI.
- Replace stale no-op/in-memory status summaries with accurate runtime descriptions.
- Clarify remaining gaps without overstating end-to-end validation.
- Keep `docs/Especificacao_Tecnica.md` untouched and reference it as the source document where needed.
- Remove or fix stale links to missing documentation files.

**Non-Goals:**

- No Java, Terraform, workflow, script, or test behavior changes.
- No changes to `docs/Especificacao_Tecnica.md`.
- No updates to binary deliverables such as PDF or PowerPoint files.
- No new CI validation for documentation drift.

## Decisions

### Treat Code and Terraform as Runtime Evidence

Use current source files, tests, Terraform, Make targets, and scripts to describe implementation status. This avoids copying older status prose and aligns with the repository guidance that status summaries can lag behind code.

Alternative considered: update docs only against `docs/Especificacao_Tecnica.md`. That would preserve a clean reference view, but it would not fix the specific problem: derived docs claim the runtime is older than it is.

### Preserve Target vs Current vs Validated Distinctions

Where a flow is implemented but not fully validated through a single persistent fakecloud E2E path, documentation should say so explicitly. For example, `feedback-api` has DynamoDB/SNS adapters and tests, while local E2E scripts may still contain assumptions or caveats that need review before claiming full pipeline validation.

Alternative considered: declare the whole `POST -> DynamoDB -> SNS -> notifier` pipeline complete everywhere. That risks overstating operational proof if scripts or fakecloud behavior have not been re-run after the implementation changes.

### Update OpenAPI Description Conservatively

The OpenAPI file should continue to document the HTTP contract, not infrastructure internals. Its description can mention that repository/publisher ports persist and publish through configured adapters, but should avoid detailed operational claims better suited to architecture docs.

Alternative considered: remove all implementation status from OpenAPI. That would be clean, but a short corrected description helps prevent stale no-op claims from surviving in a primary reader-facing artifact.

### Remove Broken `pendencias.md` References or Replace Them With Inline Known Gaps

Several docs point to `docs/pendencias.md`, which does not exist. This change should either remove those references or replace them with short local sections listing known gaps.

Alternative considered: create `docs/pendencias.md`. That would be a new documentation artifact and may become another status file to maintain; the narrower fix is to remove broken links unless a dedicated pending-items document is explicitly desired later.

## Risks / Trade-offs

- Stale docs may remain if only obvious no-op terms are replaced -> Mitigation: search for `NoOp`, `in-memory`, `memoria`, `pendencias.md`, and similar status terms across `docs/` during implementation.
- Documentation may overclaim validation -> Mitigation: distinguish implemented adapters from E2E/script validation and mention tests by type only where they exist.
- The technical specification and derived docs may intentionally differ -> Mitigation: keep `docs/Especificacao_Tecnica.md` unchanged and explain that other docs describe current implementation status.
- Diagrams may become too detailed -> Mitigation: update diagrams only if they are inaccurate; otherwise leave high-level target flow diagrams intact.
