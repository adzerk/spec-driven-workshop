## Context

This repository teaches spec-driven development with Java, JML, and JUnit 5. It already treats specs, tests, and evidence as related artifacts, but there is no automated mechanism that connects OpenSpec requirements in canonical spec files to the tests that verify them. The new traceability support must operate only on `openspec/**/spec.md`, integrate with the existing JUnit 5 and jqwik-based test suite, and preserve the current ergonomics where `make test` runs tests in isolation while `make check` performs stricter verification.

The repository currently has no production OpenSpec capability specs under `openspec/specs/`, so this change will establish the conventions and test harness needed for future capabilities. The design must surface provenance for unknown or uncovered identifiers, detect cross-file duplicates, and keep validation behavior deterministic across CLI, IDE, and CI usage.

## Goals / Non-Goals

**Goals:**
- Provide bidirectional, author-visible traceability between OpenSpec spec identifiers and JUnit 5 test methods.
- Parse uppercase kebab-style identifiers from canonical spec files and retain file-and-line provenance for diagnostics.
- Validate traced test references against the full set of canonical spec files during any traced test run.
- Enforce uncovered-spec coverage only when `spec.trace.coverage=true`, which `make check` will set explicitly.
- Support both ordinary JUnit 5 `@Test` methods and jqwik `@Property` methods.
- Keep the implementation lightweight and local to the test harness without changing production APIs.

**Non-Goals:**
- Inferring traceability from test names, display names, or implementation code without explicit declarations.
- Recognizing spec files outside `openspec/**/spec.md`.
- Enforcing coverage during `make test` or other partial developer runs by default.
- Adding class-level or package-level traceability inheritance in v1.
- Building a general-purpose markdown parser for all OpenSpec semantics beyond identifier discovery and nearby requirement/scenario context.

## Decisions

### Identifier syntax and bracket delimiters

A spec identifier is an uppercase kebab-style token matching the regex `[A-Z][A-Z0-9]*(-[A-Z0-9]+)+`. In canonical spec markdown, identifiers are written inside required square brackets (e.g., `[BOX-NULL-REJECT]`). The brackets are machine-extraction delimiters; they are not part of the identifier itself. Authors should place identifiers on `Requirement` or `Scenario` headings so the identifier and its human-readable statement stay co-located. In the `@SpecTrace` annotation, the bare identifier is used without brackets (e.g., `@SpecTrace({"BOX-NULL-REJECT"})`). Bare uppercase kebab tokens without brackets in spec files are not extracted. Bracketed tokens that do not match the identifier pattern (e.g., `[some link text]`, `[123]`) are ignored.

Alternatives considered:
- Undelimited identifiers were rejected because they would produce false positives on common uppercase words and acronyms in prose.
- Using a custom prefix like `SPEC:` was rejected in favor of standard markdown bracket syntax which is already familiar.

### Use explicit method-level annotations for test references

Traced tests will declare identifiers with a dedicated annotation such as `@SpecTrace({"BOX-NULL-REJECT"})` on each test method. This keeps identifiers visible in the test source, works for both `@Test` and `@Property`, and avoids brittle inference from naming conventions. Malformed identifiers (those not matching the identifier pattern) will produce a format-error diagnostic distinct from an unknown-identifier error. An empty identifier array (`@SpecTrace({})`) is treated as an error because it declares traceability intent without a reference. Tests without a `@SpecTrace` annotation are completely unaffected by the harness.

Alternatives considered:
- Naming-based inference from method names was rejected because it hides the contract and makes refactors error-prone.
- Class-level annotations were deferred because requirement granularity is expected at the scenario/assertion level, not the fixture level.

### Parse identifiers from canonical OpenSpec files with a lightweight catalog builder

The harness will scan `openspec/**/spec.md` and extract identifiers from required bracketed tokens such as `[BOX-NULL-REJECT]`. Authors should place these identifiers on `Requirement` or `Scenario` headers, and the parser will preserve the surrounding heading text as provenance context while omitting the bracketed identifier token from the stored heading label. The parser remains tolerant of bracketed identifiers elsewhere in non-code prose for compatibility, but ignores tokens that appear inside fenced code blocks or inline code spans so prose examples and regex fragments do not become catalog entries accidentally. Each discovered identifier will record the defining file, line number, and nearest requirement/scenario heading for provenance. The heading context is included in diagnostics when available to help authors locate the relevant requirement.

Alternatives considered:
- Restricting identifiers to headings only was rejected because the protocol expects identifiers to be recognized regardless of document position.
- A full markdown AST parser was rejected for v1 because the repository only needs stable identifier extraction and context capture.

### Fail fast on invalid references and duplicate definitions

The catalog builder will reject cross-file duplicates at catalog-building time, before any traced test executes. Reuse of the same identifier within a single spec file is intentionally permitted; the catalog records the first occurrence for provenance. During test execution, a traced method referencing an unknown identifier will cause that specific test method to fail, while other test methods in the run continue executing normally. Duplicate and unknown-reference failures are always active whenever traced tests participate in a run.

Alternatives considered:
- Reporting duplicates only during coverage runs was rejected because duplicate identifiers corrupt the catalog even for subset runs.
- Silently ignoring unknown identifiers was rejected because it defeats traceability guarantees.
- Aborting the entire test plan on unknown references was rejected in favor of per-method failure so that a single run surfaces all diagnostic information.

### Empty catalog behavior

When no canonical spec files exist under `openspec/**/spec.md` or they contain no valid bracketed identifiers, the catalog is empty. Traced tests referencing identifiers against an empty catalog fail as unknown references (per-method). Coverage enforcement against an empty catalog trivially passes because there are no identifiers to cover.

### Separate validation from coverage with an explicit system property

Coverage enforcement will be controlled only by `spec.trace.coverage=true`. `make check` will set the property, and `make test` will leave it unset. The traceability layer will read the property directly via `System.getProperty("spec.trace.coverage")` instead of inferring execution intent from Surefire filters or test-plan size.

Alternatives considered:
- Inferring full-suite execution from the JUnit test plan or Surefire arguments was rejected because it is fragile across IDE, Maven, and CI environments.
- A second custom profile or separate verification command was rejected because a single explicit property is simpler and matches the user's requirement.

### Pass the coverage property via Makefile CLI argument

The `make check` recipe will pass `-Dspec.trace.coverage=true` as a Maven CLI argument. No Maven profile is added for this property. IDE users who want coverage enforcement must set the system property in their run configuration manually.

Alternatives considered:
- A Maven profile activating the property in Surefire's `systemPropertyVariables` was rejected to keep `pom.xml` simple and avoid profile proliferation.
- A combined approach (CLI + profile) was considered but adds complexity without sufficient benefit.

### Use JUnit 5 extension and suite-level aggregation

Traceability validation will be implemented as JUnit 5 test-support code that inspects annotated methods during execution and records the identifiers exercised in the run. Coverage checks will be emitted once per overall test run when coverage is enabled, using a suite-level lifecycle hook or root-store close mechanism to avoid per-class false negatives.

Alternatives considered:
- A standalone Maven plugin was rejected because it would be less natural for JUnit/jqwik tests and harder to align with method-level execution context.
- Per-class `@AfterAll` coverage checks were rejected because they cannot reliably reason about full-suite coverage.

### Support review-only requirements through ordinary traced passing tests

Requirements that cannot be automatically verified will still participate in traceability via a passing traced test that documents the reasoning in a comment. The harness does not need a special identifier type; it only needs the same declaration and coverage accounting path.

Alternatives considered:
- Adding a special annotation for review-only requirements was rejected for v1 because the existing traced-test mechanism is sufficient.

## Preconditions, Postconditions, and Invariants

### Catalog building

**Preconditions:**
- The project root directory is accessible at catalog build time.
- `openspec/**/spec.md` paths are resolved relative to the project root.

**Postconditions:**
- Every `SpecIdDefinition` in the catalog has a non-null, non-empty identifier matching `[A-Z][A-Z0-9]*(-[A-Z0-9]+)+`, a non-null file path, and a positive line number.
- No two definitions in the catalog share the same identifier text (cross-file uniqueness). If duplicates are found, catalog building fails with provenance for all conflicting definitions.
- Within-file duplicate identifiers are permitted; the catalog records the first occurrence.

### Validation

**Invariants:**
- Reference validation (unknown-identifier and format checks) is always active when at least one traced test is in the execution plan.
- Coverage enforcement is active if and only if `System.getProperty("spec.trace.coverage")` equals `"true"`.
- Tests without `@SpecTrace` are invisible to the harness and execute without interference.

**Per-method behavior:**
- A traced test method with a malformed identifier fails with a format error.
- A traced test method with an empty identifier array fails with a configuration error.
- A traced test method referencing an unknown identifier fails; other methods continue.
- A traced test method referencing valid, known identifiers executes normally.

### Coverage

**Postconditions:**
- The set of exercised identifiers grows monotonically during a test run (identifiers are added, never removed).
- When coverage is enabled and the run completes, every identifier in the catalog is present in the exercised set, or the run fails with provenance for each missing identifier.
- An empty catalog with coverage enabled trivially passes.

## Risks / Trade-offs

- [Spec parser is too permissive and captures accidental bracketed tokens] -> Require brackets as delimiters and limit recognition to uppercase kebab-style tokens matching a strict regex. Add parser fixtures for false-positive cases.
- [Coverage aggregation is brittle across JUnit execution modes] -> Keep the aggregation inside JUnit 5 lifecycle mechanisms and back it with integration tests that run under Maven/Surefire.
- [Developers forget to annotate tests for new specs] -> Coverage enforcement in `make check` catches missing mappings before code is considered verified.
- [Diagnostics become hard to interpret when many ids exist] -> Include precise file-and-line provenance plus nearby requirement/scenario context in errors.
- [Traceability support adds friction to quick local runs] -> Leave coverage disabled for `make test` and keep unknown-reference validation limited to explicitly traced tests.

## Migration Plan

- Add the traceability support classes, annotations, and fixtures in the test-support area.
- Add the new `spec-traceability` capability spec defining identifier syntax, validation, provenance, and coverage behavior.
- Update the `make check` recipe in the Makefile to pass `-Dspec.trace.coverage=true` on the Maven command line, leaving `make test` unchanged.
- Add representative traced tests that prove ordinary tests, jqwik properties, duplicate detection, unknown references, malformed identifiers, empty annotations, empty catalogs, and uncovered identifiers behave as intended.
- Adopt the annotation convention in future capability tests as new canonical specs are added under `openspec/specs/`.

## Open Questions

- None for v1. The canonical spec location, identifier syntax with required bracket delimiters, annotation granularity, failure granularity, empty catalog behavior, and coverage toggle mechanism are all decided.
