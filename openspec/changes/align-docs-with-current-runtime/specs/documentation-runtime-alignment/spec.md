## ADDED Requirements

### Requirement: Derived documentation reflects current runtime state
Documentation derived from the technical specification SHALL describe the current implementation state of runtime components using the repository source, tests, Terraform, scripts, and CI as evidence.

#### Scenario: AWS adapters are active in code
- **WHEN** documentation describes `feedback-api` persistence or critical event publishing
- **THEN** it SHALL reflect that the active runtime adapters persist feedbacks through DynamoDB and publish critical events through SNS rather than describing those paths as in-memory/no-op.

#### Scenario: Critical notifier runtime is described
- **WHEN** documentation describes `critical-notifier`
- **THEN** it SHALL reflect SNS event handling, SES e-mail sending, and DynamoDB-backed idempotency rather than describing a simple input handler with a no-op e-mail gateway.

### Requirement: Technical specification remains unchanged
The documentation alignment change SHALL NOT modify `docs/Especificacao_Tecnica.md` because it is the reference document for expected architecture and requirements.

#### Scenario: Implementation updates documentation
- **WHEN** the documentation alignment work is applied
- **THEN** `docs/Especificacao_Tecnica.md` SHALL remain byte-for-byte untouched by the change.

### Requirement: Documentation distinguishes implementation from validation
Documentation SHALL distinguish between implemented code paths, tested behavior, local fakecloud/E2E coverage, and remaining operational gaps.

#### Scenario: End-to-end pipeline status is documented
- **WHEN** documentation discusses the `POST /avaliacao -> DynamoDB -> SNS -> critical-notifier` pipeline
- **THEN** it SHALL avoid claiming the pipeline is still blocked by missing adapters and SHALL separately state any remaining validation, script, fakecloud, or operational caveats.

#### Scenario: Test coverage is documented
- **WHEN** documentation summarizes tests
- **THEN** it SHALL reflect existing unit and integration test classes, including current `*IT.java` coverage where applicable.

### Requirement: Documentation has no stale broken references
Documentation under `docs/` SHALL NOT point readers to missing local markdown files for required follow-up context.

#### Scenario: Missing pendencias link is found
- **WHEN** implementation finds a reference to `docs/pendencias.md` or `pendencias.md` and that file does not exist
- **THEN** the reference SHALL be removed, replaced with inline known gaps, or redirected to an existing artifact.
