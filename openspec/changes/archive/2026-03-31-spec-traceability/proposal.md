## Why

OpenSpec specifications currently describe requirements and scenarios, but the repository does not have a built-in way to prove which tests verify which claims or where an identifier was defined. Adding provenance-aware traceability makes the workshop's evidence model explicit, lets `make check` detect drift between specs and tests, and keeps ordinary `make test` runs lightweight.

## What Changes

- Add provenance-aware spec traceability for canonical spec files located at `openspec/**/spec.md`.
- Define a machine-readable identifier convention for traced requirements and scenarios using uppercase kebab-style identifiers such as `BOX-NULL-REJECT`, with identifiers intended to be placed on `Requirement` or `Scenario` headers.
- Add JUnit 5 test-harness support so traced test methods can declare the spec identifiers they verify.
- Validate traced identifiers against the full project spec catalog and report unknown references and cross-file duplicate identifiers with file-and-line provenance.
- Enforce uncovered-spec coverage only when `spec.trace.coverage=true`, which will be enabled by `make check` and left disabled for `make test`.
- Add tests and fixtures that demonstrate normal traced tests, jqwik property tests, provenance reporting, and coverage toggling.

## Capabilities

### New Capabilities
- `spec-traceability`: Bidirectional traceability between OpenSpec spec identifiers and JUnit 5 tests, including provenance-aware diagnostics and coverage enforcement controls.

### Modified Capabilities
- None.

## Impact

- Adds new OpenSpec spec artifacts under `openspec/changes/spec-traceability/specs/`.
- Adds JUnit 5 test-support code and fixtures in the test source tree.
- Updates the Make-driven verification path so `make check` enables spec coverage enforcement while `make test` does not.
- Improves diagnostics for specification/test mismatches without changing production application APIs.
