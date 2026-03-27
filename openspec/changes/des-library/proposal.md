## Why

The workshop currently demonstrates JML and testing with isolated examples, but it does not include a reusable state machine or event-processing library that exposes richer behavioral contracts. Adding a discrete event simulation library creates a compact, realistic teaching target for immutable design, deterministic behavior, property-based testing, and selective JML verification.

## What Changes

- Add a new immutable discrete event simulation library under `com.kevel.sim` for modeling state transitions over discrete time.
- Introduce caller-facing scheduling, inspection, single-step execution, and bounded execution APIs.
- Define deterministic event ordering, validation rules, and failure behavior for malformed actions.
- Add OpenSpec requirements, design notes, and implementation tasks covering JML boundaries and test strategy.

## Capabilities

### New Capabilities
- `simulation-engine`: Deterministic discrete-time event scheduling and execution with immutable state snapshots and validated action results.

### Modified Capabilities
- None.

## Impact

- Affected code: new production classes under `src/main/java/com/kevel/sim/` and new tests under `src/test/java/com/kevel/sim/`.
- APIs: introduces a new public simulation API centered on immutable state, event specifications, and simulator operations.
- Dependencies and systems: uses existing Maven, JUnit 5, jqwik, and OpenJML tooling already present in the workshop.
