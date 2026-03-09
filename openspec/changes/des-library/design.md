## Context

The repository currently contains a minimal arithmetic example that demonstrates JML contracts and property-based testing, but it does not yet show how specification-driven development scales to a richer domain with state, ordering, and execution semantics.
A discrete-event simulation library is a good next step because it is small enough to stay teachable while still exposing meaningful invariants: time must not move backward, equal-time events need deterministic total-ordering, and all execution must be explainable in terms of pure state transitions.

This change should fit the existing workshop stack: Java 21, JUnit 5, jqwik, static analysis, and formal-style contract thinking. The implementation should stay intentionally small, single-threaded, and dependency-free.

## Goals / Non-Goals

**Goals:**
- Provide a compact public API for creating simulations, scheduling work, stepping execution, and running to defined boundaries.
- Preserve deterministic execution through explicit ordering by `(time, sequence)`.
- Construct the core library in a functional style and use pure methods and functions.
- Use `long` ticks as the time model for v1 to avoid floating-point ambiguity.
- Design the library around single-threaded execution.
- Document all preconditions, postconditions, invariants, and error behavior in a way that supports the workshop's specification-driven teaching goals.
- Validate the design with both example-based tests and jqwik properties, including state-machine-oriented checks.

**Non-Goals:**
- Multi-threaded simulation or thread-safe access.
- Event cancellation, event priorities beyond time and insertion order, or distributed execution.
- Wall-clock integration, asynchronous I/O, or real-time scheduling.
- Enforcing deep immutability of user state at runtime.
- Supporting floating-point time in v1.

## Decisions

### Use a small library under `com.kevel.des`
- Decision: Add a focused package with a few public types such as `Simulation<S>`, `EventId`, `StepResult<S>`, and `RunResult<S>`.
- Rationale: A small surface area keeps the workshop approachable and makes the contracts easier to understand and test.
- Alternatives considered:
  - Extend the existing `App` example directly: rejected because it would mix a toy demonstration with a reusable teaching example.
  - Build a more domain-specific simulator (for example queueing or inventory): rejected because generic simulation primitives better illustrate reusable contracts.

### Model event actions as functional state transitions
- Decision: Events carry an action that transforms `S -> S`, and the engine acts as a deterministic interpreter over those actions.
- Rationale: This matches the workshop's emphasis on specification and reasoning. It lets tests and contracts focus on state transitions instead of side effects.
- Alternatives considered:
  - Allow arbitrary callbacks with side effects as the main abstraction: rejected because it weakens determinism and muddies the workshop's functional core story.
  - Require a fully persistent immutable engine API: rejected for v1 because it complicates internals without adding much teaching value.

### Use `long` ticks for time representation
- Decision: Represent simulation time as `long` ticks.
- Rationale: `long` provides a total order, integrates cleanly with overflow checks, and avoids floating-point tie and precision behavior that would distract from the core lesson.
- Alternatives considered:
  - `double` time: more flexible for some models, but introduces comparison edge cases and ambiguous equal-time behavior.
  - `Duration`: more semantic, but heavier than needed for a compact educational library.

### Use `(time, sequence)` ordering with a priority queue
- Decision: Store pending events in a priority queue ordered first by event time and then by a monotonically increasing sequence number assigned at scheduling time.
- Rationale: This yields deterministic FIFO behavior for equal-time events and a straightforward invariant that can be tested and documented.
- Alternatives considered:
  - Order only by time: rejected because equal-time execution order would become implementation-dependent.
  - Use a sorted map from time to lists: possible, but more structure than needed for v1.

### Validate behavior with layered tests
- Decision: Combine focused unit tests with jqwik property tests, including model/state-machine validation against a simpler reference interpretation.
- Rationale: Example-based tests cover API expectations, while property-based tests better express determinism, monotonic time, and execution ordering across many schedules.
- Alternatives considered:
  - Unit tests only: rejected because they would underspec the richer state space.
  - Property tests only: rejected because API ergonomics and failure modes are easier to express in direct example tests.

### Use modern Java 21 features
- Decision: When selecting implementation options, favor using modern Java features like records, sealed interfaces, switch expressions, and generics.
- Rationale: Modern Java language features make code more readable and maintainable.
- Alternatives considered:
  - Basic features: rejected because the engineers understand how to use more advanced language features

## Risks / Trade-offs

- [Users pass mutable state or impure actions] -> Mitigation: document the functional-style expectation clearly and keep deterministic engine semantics separate from purity guarantees.
- [Overflow and invalid time arithmetic create subtle failures] -> Mitigation: define explicit failure behavior for negative delays, past scheduling, and `long` overflow.
- [API grows beyond a teaching example] -> Mitigation: keep v1 intentionally narrow and treat cancellation, concurrency, and richer scheduling semantics as future changes.
- [State-machine property tests become too complex for workshop readers] -> Mitigation: pair them with a simple reference model and keep the properties focused on a few core invariants.

## Migration Plan

This is an additive change with no production deployment concerns. The implementation can land incrementally in the following order:

1. Add the new `com.kevel.des` package and core public types.
2. Implement creation, scheduling, stepping, and run semantics.
3. Add contract-oriented Javadoc and assertions or checks for invalid usage.
4. Ensure the code is defensive and enforces all pre-conditions, post-conditions, and invariants.
4. Add unit and jqwik tests.
5. Add a minimal example and ensure the existing workshop checks still pass.

Rollback is straightforward: remove the new package and associated tests if the design proves unsuitable before adoption.

## Open Questions

- Should `step()` and run operations mutate the `Simulation<S>` instance in place or return derived result objects while keeping the simulation mutable internally? The current direction is mutable internals with deterministic behavior and explicit result records.
- Should `peekNext()` expose the full event record or a reduced public view to avoid encouraging dependence on internal sequencing details? The current direction is to expose the full event record.
- How much JML annotation should v1 include in addition to Javadoc contracts, given the workshop may want both lightweight and heavier-weight specification examples?  The current direction is only document invariants and not include JML annotations.
