# Architecture Contract

This document defines enforceable dependency rules for clean layering in this project.

## Layers

1. `domain`: thermodynamic/identification models and pure computation logic.
2. `application`: use-case orchestration and ports.
3. `infrastructure`: persistence/resource adapters implementing ports.
4. `presentation`: GUI/CLI adapters and view concerns.

## Dependency Rules

### Rule A: Domain purity
- Files under `org/ce/domain/**` MUST NOT import:
- `org.ce.application.`
- `org.ce.infrastructure.`
- `org.ce.presentation.`

### Rule B: Application isolation
- Files under `org/ce/application/**` MUST NOT import:
- `org.ce.infrastructure.`
- `org.ce.presentation.`

### Rule C: Presentation legacy guard
- Files under `org/ce/presentation/**` MUST NOT import:
- `org.ce.workbench.`

## Transitional Policy

The `org.ce.workbench.*` transition namespace has been removed from production code.
Architecture tests keep a legacy guard to prevent accidental reintroduction.

## Migration Guidance

- Move types, not behavior first.
- Keep adapters at boundaries while shifting canonical models into `org.ce.domain`.
- Keep boundary tests strict and avoid adding allowlists unless absolutely necessary.

## CI Expectation

Architecture boundary tests are required for all PRs. A PR fails if a new file violates a boundary rule.
