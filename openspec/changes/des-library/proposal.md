## Why

The workshop currently demonstrates specification-driven development with a very small arithmetic example, which is useful but too narrow to show how contracts, properties, and design decisions interact in a richer domain. A small discrete-event simulation library adds a realistic but still teachable problem space with explicit ordering, time, invariants, and state transitions.

## What Changes

- Add a new discrete-event simulation library under `com.kevel.des` for single-threaded, deterministic execution.
- Introduce a small public API for creating simulations, scheduling events, stepping execution, and running until completion or a stopping condition.
- Define and document the library's behavioral contracts, including preconditions, postconditions, invariants, and error cases.
- Add tests that validate deterministic ordering, time monotonicity, invalid scheduling behavior, overflow handling, and state-machine properties.
- Add a minimal example that demonstrates how the library supports functional-style model updates.

## Capabilities

### New Capabilities
- `discrete-event-simulation`: Single-threaded simulation engine semantics, scheduling behavior, execution ordering, and run controls for deterministic model evolution.

### Modified Capabilities
- None.

## Impact

- Adds new production code in `src/main/java/com/kevel/des/`.
- Adds new tests in `src/test/java/com/kevel/des/` using JUnit 5 and jqwik.
- Expands the workshop from a toy example to a reusable teaching example for specification-driven design.
- Introduces a new public API surface that should remain intentionally small and well-documented.
