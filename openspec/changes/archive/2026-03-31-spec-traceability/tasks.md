## 1. Spec catalog and identifier parsing

- [x] 1.1 Add test-support types for spec identifier definitions, including identifier text, defining path, line number, and nearby requirement or scenario heading context
- [x] 1.2 Implement canonical spec discovery for `openspec/**/spec.md` that parses required bracketed uppercase kebab-style identifiers (matching `[A-Z][A-Z0-9]*(-[A-Z0-9]+)+`), with identifiers intended to live on `Requirement` and `Scenario` headers, while ignoring non-canonical files, bare unbracketed tokens, and bracketed tokens that do not match the identifier pattern
- [x] 1.3 Implement cross-file duplicate detection that fails catalog building with provenance for all conflicting definitions; permit within-file duplicates and record the first occurrence
- [x] 1.4 Add tests for canonical discovery, bracket-required extraction, non-matching bracket rejection, within-file duplicate tolerance, cross-file duplicate rejection, empty catalog, malformed identifiers, and provenance capture including nearest heading context

## 2. JUnit 5 traceability integration

- [x] 2.1 Add a method-level `@SpecTrace` annotation for traced test declarations that accepts one or more bare spec identifiers on JUnit 5 `@Test` and jqwik `@Property` methods
- [x] 2.2 Implement JUnit 5 traceability validation that loads the full canonical spec catalog, rejects unknown identifiers with per-method failure (other methods continue), rejects malformed identifiers with a distinct format-error diagnostic, rejects empty identifier arrays as configuration errors, and records exercised identifiers during execution
- [x] 2.3 Verify that tests without `@SpecTrace` are completely unaffected by the harness and execute normally
- [x] 2.4 Add integration tests proving traced `@Test` and `@Property` methods validate identifiers against the full canonical catalog during subset runs, and that per-method failure does not prevent other methods from executing

## 3. Coverage enforcement and build wiring

- [x] 3.1 Implement suite-level coverage reporting that fails only when `spec.trace.coverage=true` and includes provenance (file, line, nearest heading) for each uncovered identifier; an empty catalog with coverage enabled trivially passes
- [x] 3.2 Update the `make check` Makefile recipe to pass `-Dspec.trace.coverage=true` as a Maven CLI argument while leaving `make test` unchanged
- [x] 3.3 Add tests for coverage-on versus coverage-off behavior, empty-catalog coverage pass, and provenance-rich diagnostics for unknown, malformed, duplicate, empty-annotation, and uncovered identifiers

## 4. Adoption fixtures and documentation

- [x] 4.1 Add canonical OpenSpec fixtures under test resources that demonstrate valid bracketed identifiers on `Requirement` and `Scenario` headers, duplicate identifiers across files, within-file duplicates, non-matching brackets, bare tokens, empty catalogs, and review-only traced requirements
- [x] 4.2 Annotate representative workshop tests or dedicated harness tests to show the expected authoring pattern for `@SpecTrace`, including multi-identifier declarations and review-only requirements
- [x] 4.3 Document the traceability conventions in `docs/spec-traceability.md` for future contributors, covering canonical spec location, required bracket delimiters, identifier syntax pattern, `@SpecTrace` annotation usage, `spec.trace.coverage` toggle behavior, and failure granularity
