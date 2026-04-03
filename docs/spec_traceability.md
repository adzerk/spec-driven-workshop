# Spec Traceability

`spec-traceability` connects canonical OpenSpec identifiers to JUnit 5 and jqwik test methods.

## Canonical spec files

- Canonical identifiers are discovered only from `openspec/**/spec.md`.
- Identifiers must appear in square brackets in markdown, for example `[BOX-NULL-REJECT]`.
- Identifiers should be placed on `Scenario` or `Requirement` headers.
- The extracted identifier excludes the brackets, must match `[A-Z][A-Z0-9]*(-[A-Z0-9]+)+` and should be less than 30 characters.
- Bare uppercase kebab tokens without brackets are ignored.
- Bracketed tokens inside inline code spans or fenced code blocks are ignored.

### Identifier style

Identifiers should be semantic, not implementation-specific.

- Good: `BOX-NULL-REJECT`
- Worse: `BOX-USE-OBJECTS-REQUIRE-NON-NULL`

Identifiers work best when each one names a single observable behavior or obligation.

Prefer identifiers that are:
- behavior-oriented
- stable even if implementation details change
- small enough to map naturally to one or a few traced tests

Avoid identifiers that:
- encode implementation choices
- bundle many unrelated behaviors together
- describe internal refactorings rather than externally visible guarantees

For best results with agent-assisted development, write `Requirement` and `Scenario` text so each tagged header clearly states:
- the condition or input
- the expected outcome
- any important edge case or failure mode

## Declaring traced tests

Annotate each traced method explicitly with `@SpecTrace` via `com.kevel.spectrace.SpecTrace`.

```java
@Test
@SpecTrace({"BOX-NULL-REJECT"})
void ofRejectsNullValues() {
    assertThrows(NullPointerException.class, () -> Box.of(null));
}

@Property
@SpecTrace({"BOX-INTO-PRESERVES-VALUE", "BOX-INTO-CHANGES-TAG"})
void intoPreservesUnderlyingValue(@ForAll int value) {
    assertEquals(value, Box.<Years, Integer>of(value).into().get());
}
```

- `@SpecTrace({})` is an error.
- Malformed identifiers in `@SpecTrace` fail with a format error.
- Unknown identifiers fail only the declaring test method.
- Tests without `@SpecTrace` are unaffected.

## Coverage behavior

- Reference validation for traced tests always runs.
- Coverage enforcement runs only when `spec.trace.coverage=true`.
- `make check` enables coverage with `-Dspec.trace.coverage=true`.
- `make test` leaves coverage disabled for lightweight local runs.
- When coverage is enabled, every canonical identifier must be declared by at least one traced test in the run.
- An empty catalog trivially passes coverage.

## Diagnostics

Diagnostics include provenance for canonical definitions when available:

- defining file
- defining line number
- nearest `Requirement` or `Scenario` heading

Cross-file duplicate identifiers fail catalog building before traced methods run. Repeated identifiers within a single spec file are allowed and keep the first occurrence for provenance.

## Review-only requirements

If a requirement is verified by reasoning rather than executable assertions, use a traced phony/`assertTrue(true)` passing test and explain the reasoning in a short comment.

## Recommended workflow

For new work, add canonical identifiers to important `Requirement` and `Scenario` headers before implementation begins.

Use identifiers to keep the spec authoritative and to give implementation, tests, and review a shared reference point.

By convention, include identifiers in Javadocs of methods that implement the functionality of a given scenario or enforce a given invariant.

A final review pass should still confirm that:
- identifier granularity is appropriate
    - split identifiers when one tag covers too much behavior
    - merge identifiers when multiple tags describe the same obligation
- traced tests match the intended requirements
- some requirements are marked as review-only when executable assertions are not the right verification mechanism

For existing or legacy specs, adding identifiers during review is a reasonable retrofit step. For new changes, prefer tagging the spec early.

