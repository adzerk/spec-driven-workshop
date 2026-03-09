## Context

The repository currently contains a minimal arithmetic example that demonstrates JML contracts and property-based testing, but it does not yet show how specification-driven development scales to a richer domain with state, ordering, and execution semantics.
A discrete-event simulation library is a good next step because it is small enough to stay teachable while still exposing meaningful invariants: time must not move backward, equal-time events need deterministic total-ordering, and all execution must be explainable in terms of pure state transitions.

This change should fit the existing workshop stack: Java 21, JUnit 5, jqwik, static analysis, OpenJML, and formal-style contract thinking. The implementation should stay intentionally small, single-threaded, and dependency-free.

## Goals / Non-Goals

**Goals:**
- Provide a compact public API for creating simulations, scheduling work, stepping execution, and running to defined boundaries.
- Preserve deterministic execution through explicit ordering by `(time, sequence)`.
- Construct the core library in a functional style: event actions are pure `S -> ActionResult<S>` functions that return both new state and declarative scheduling intent.
- Use `long` ticks as the time model for v1 to avoid floating-point ambiguity.
- Design the library around single-threaded execution.
- Use value-oriented event records and queue semantics so the event pipeline is fully data-driven.
- Document all preconditions, postconditions, invariants, and error behavior in a way that supports the workshop's specification-driven teaching goals.
- Include targeted JML annotations on critical types and operations to demonstrate formal specification scaling beyond the existing toy example.
- Validate the design with both example-based tests and jqwik properties, including state-machine-oriented checks.

**Non-Goals:**
- Multi-threaded simulation or thread-safe access.
- Event cancellation, event priorities beyond time and insertion order, or distributed execution.
- Wall-clock integration, asynchronous I/O, or real-time scheduling.
- Enforcing deep immutability of user state at runtime.
- Supporting floating-point time in v1.
- Exhaustive or complete JML annotation coverage; annotations are pedagogical, not production-grade verification.

## Decisions

### Use a small library under `com.kevel.des`
- Decision: Add a focused package with public types: `Simulation<S>`, `ActionResult<S>`, `Event<S>`, `EventId`, `StepResult<S>`, and `RunResult<S>`. Use record types where appropriate.
- Rationale: A small surface area keeps the workshop approachable and makes the contracts easier to understand and test.
- Alternatives considered:
  - Extend the existing `App` example directly: rejected because it would mix a toy demonstration with a reusable teaching example.
  - Build a more domain-specific simulator (for example queueing or inventory): rejected because generic simulation primitives better illustrate reusable contracts.

### Model event actions as pure functions returning `ActionResult<S>`
- Decision: Event actions have the signature `Function<S, ActionResult<S>>`, where `ActionResult<S>` is a record containing the new state and a list of event descriptors (time + action pairs) to schedule. The engine interprets these descriptors after applying the action, inserting them into the queue with proper sequence numbering.
- Rationale: This preserves action purity (`S -> ActionResult<S>` is a pure function) while enabling the common DES pattern of events scheduling follow-on events. Actions never interact with the engine directly; all scheduling intent is expressed declaratively through the return value. This keeps the functional core clean and makes actions independently testable.
- Alternatives considered:
  - `UnaryOperator<S>` with no self-scheduling: simpler but prevents the most natural DES patterns where events chain to subsequent events. Would force all event schedules to be set up externally before running.
  - Allow actions to call `sim.scheduleAt()` directly (re-entrant mutation): rejected because it breaks action purity, makes actions untestable in isolation, and creates complex re-entrancy semantics.
  - Return a separate `Effect` type that the engine interprets: essentially what `ActionResult` is, but a more generic name that might suggest side effects rather than value-oriented intent.

### Use value-oriented event records and queue
- Decision: Events are immutable records containing `(time, sequence, eventId, action)`. The queue is managed internally as a priority structure ordered by `(time, sequence)`. Sequence numbers are assigned monotonically at scheduling time regardless of whether the event came from an external `scheduleAt`/`scheduleIn` call or from an `ActionResult`.
- Rationale: Value-oriented events make the system easier to reason about, test, and specify. Immutable records are natural in Java 21 and align with the functional core principle.
- Alternatives considered:
  - Mutable event objects: rejected because mutability would undermine the value-oriented design and complicate contract reasoning.
  - Separate sequence counters for external vs. action-produced events: rejected because a single global counter is simpler and produces a cleaner total order.

### Use `long` ticks for time representation
- Decision: Represent simulation time as `long` ticks.
- Rationale: `long` provides a total order, integrates cleanly with overflow checks (`Math.addExact`), and avoids floating-point tie and precision behavior that would distract from the core lesson.
- Alternatives considered:
  - `double` time: more flexible for some models, but introduces comparison edge cases and ambiguous equal-time behavior.
  - `Duration`: more semantic, but heavier than needed for a compact educational library.

### Use `(time, sequence)` ordering with a priority queue
- Decision: Store pending events in a priority queue ordered first by event time and then by a monotonically increasing sequence number assigned at scheduling time.
- Rationale: This yields deterministic FIFO behavior for equal-time events and a straightforward invariant that can be tested and documented.
- Alternatives considered:
  - Order only by time: rejected because equal-time execution order would become implementation-dependent.
  - Use a sorted map from time to lists: possible, but more structure than needed for v1.

### `Simulation<S>` uses mutable internals with deterministic behavior
- Decision: `Simulation<S>` is a mutable class internally (manages a priority queue, time counter, state reference, and sequence counter) but exposes deterministic behavior through its public API. Step and run operations return immutable result records (`StepResult<S>`, `RunResult<S>`).
- Rationale: A fully persistent/immutable simulation engine would add implementation complexity (persistent priority queues) without meaningful pedagogical benefit for v1. The teaching value comes from the contracts and the functional action model, not from the engine's internal data structures.

### `peekNext()` exposes the full `Event<S>` record
- Decision: `peekNext()` returns `Optional<Event<S>>` containing time, sequence, event id, and action.
- Rationale: Exposing the full record keeps the API simple and avoids a separate "event view" type. The sequence number is useful for testing ordering guarantees.

### Include targeted JML annotations on critical types and operations
- Decision: Add JML annotations to the `Simulation` class (class-level invariants for monotonic time and non-negative sequence counters), to `step()` (pre/postconditions), and to at least one scheduling method (preconditions). Do not annotate every method.
- Rationale: This demonstrates JML scaling to a richer domain without overwhelming the codebase. The existing `App.java` shows JML on a single method; the DES library shows JML on a class with invariants and multiple interacting methods. Together they form a progression from simple to moderate JML usage.
- Alternatives considered:
  - No JML at all: rejected because the workshop's purpose includes teaching formal specification, and omitting JML from the main new library would miss the opportunity.
  - Exhaustive JML on every method: rejected because the annotations would dominate the code and distract from the teaching narrative. Partial annotation is more realistic and more instructive.

### Validate behavior with layered tests
- Decision: Combine focused unit tests with jqwik property tests, including model/state-machine validation against a simpler reference interpretation.
- Rationale: Example-based tests cover API expectations, while property-based tests better express determinism, monotonic time, and execution ordering across many schedules.
- Alternatives considered:
  - Unit tests only: rejected because they would underspec the richer state space.
  - Property tests only: rejected because API ergonomics and failure modes are easier to express in direct example tests.

### Use modern Java 21 features
- Decision: Favor records, sealed interfaces, switch expressions, and generics where they improve clarity.
- Rationale: Modern Java language features make code more readable and maintainable, and the workshop audience is comfortable with them.

## Risks / Trade-offs

- [Users pass mutable state or impure actions] -> Mitigation: document the functional-style expectation clearly and keep deterministic engine semantics separate from purity guarantees. The `ActionResult` pattern makes the expected return contract explicit.
- [Overflow and invalid time arithmetic create subtle failures] -> Mitigation: define explicit failure behavior for negative delays, past scheduling, `long` overflow, and action-produced events with invalid times.
- [API grows beyond a teaching example] -> Mitigation: keep v1 intentionally narrow and treat cancellation, concurrency, and richer scheduling semantics as future changes.
- [`ActionResult` adds a type that users must learn] -> Mitigation: keep it as a simple record with a static convenience factory. The tradeoff is worth it because it enables self-scheduling while preserving purity.
- [State-machine property tests become too complex for workshop readers] -> Mitigation: pair them with a simple reference model and keep the properties focused on a few core invariants.
- [JML annotations may not pass OpenJML ESC for all annotated methods] -> Mitigation: focus annotations on properties that OpenJML can verify (non-negative fields, null checks) and use `skipesc` on methods where full verification is impractical for v1.

## Migration Plan

This is an additive change with no production deployment concerns. The implementation can land incrementally in the following order:

1. Add the new `com.kevel.des` package and core public types (`EventId`, `Event<S>`, `ActionResult<S>`, `StepResult<S>`, `RunResult<S>`).
2. Implement `Simulation<S>`: creation, scheduling, stepping with `ActionResult` processing, and run semantics.
3. Add contract-oriented Javadoc and defensive validation for all preconditions.
4. Add targeted JML annotations on `Simulation` (class invariants), `step()` (pre/post), and at least one scheduling method (pre).
5. Add unit tests covering creation, scheduling, FIFO ordering, stepping, run boundaries, error cases, and self-scheduling via `ActionResult`.
6. Add jqwik property and model-based tests for determinism, monotonic time, and equivalence against a reference model.
7. Add a minimal usage example and ensure the existing workshop checks still pass.

Rollback is straightforward: remove the new package and associated tests if the design proves unsuitable before adoption.
