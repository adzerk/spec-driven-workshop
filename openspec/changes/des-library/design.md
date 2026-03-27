## Context

This change introduces a new discrete event simulation library into a repository that currently contains only a small JML-focused example program. The library should be useful as both a reusable utility and a workshop artifact that demonstrates immutable state transitions, deterministic ordering, validation at boundaries, and a realistic split between what OpenJML can verify directly and what must be checked with tests.

The design is constrained by the workshop's existing Java 21, Maven, jqwik, and OpenJML toolchain. It should remain small, single-threaded, and approachable for teaching, while still surfacing non-trivial contracts such as total event ordering and bounded execution semantics.

## Goals / Non-Goals

**Goals:**
- Provide an immutable simulation state that can be initialized, inspected, and evolved through pure-looking static operations.
- Make event execution deterministic even when multiple events share the same scheduled time.
- Separate caller-facing scheduling concerns from engine-internal ordering concerns.
- Fail fast on invalid inputs and malformed action results.
- Apply JML where it adds value without forcing the design into shapes that are difficult for OpenJML to verify.
- Support both unit tests and property-based tests that exercise contracts and invariants.

**Non-Goals:**
- Real-time or wall-clock scheduling with `Instant` or `Duration`.
- Built-in concurrency, shared mutable global state, or multi-threaded execution.
- A full actor framework, workflow runtime, or visualization layer.
- Proving user-supplied action purity or verifying arbitrary lambda bodies with OpenJML.
- An unbounded `runToCompletion` convenience API in the initial design.

## Decisions

### Immutable engine snapshot
The engine state is modeled as a `SimulationState<S>` value holding current time, the next sequence number, processed count, payload, and pending events. Keeping the entire engine snapshot in one value makes transitions explicit and easy to compare in tests.

Alternatives considered:
- Mutable simulator instance: simpler operationally, but weaker for reasoning, JML contracts, and deterministic replay.
- Splitting queue metadata across separate helper types: possible, but adds indirection without clear teaching value.

### Deterministic total ordering via internal sequence numbers
Pending events are ordered by `(scheduledAt, sequenceNumber)` ascending. Callers schedule `EventSpec<S>` values that omit sequence numbers; the engine assigns sequence numbers as events enter the queue.

This preserves deterministic execution order for ties without forcing callers to know engine internals.

Alternatives considered:
- Ordering by time only: insufficient because simultaneous events would become ambiguous.
- Letting callers provide sequence numbers: leaks implementation details and invites invalid states.

### Caller actions return payload plus emitted events
`EventAction<S>` returns a `TransitionResult<S>` containing the next payload and zero or more emitted events. This keeps all payload changes explicit and makes event emission part of the step contract.

Alternatives considered:
- Letting actions mutate state directly: undermines immutable reasoning and complicates tests.
- Passing the full simulation state into actions: exposes queue internals and sequence counters that should remain engine-owned.

### Non-null payload and explicit validation boundaries
Payloads are always non-null, with stateless models expected to use `Void` or a unit-like record. Public APIs reject null arguments with `NullPointerException`, domain violations with `IllegalArgumentException`, and malformed action results with `IllegalStateException`.

This makes illegal states fail at the boundary rather than allowing partially valid states to propagate.

Alternatives considered:
- Nullable payloads: increases special-case handling throughout the API and weakens invariants.
- One exception type for all validation failures: simpler mechanically, but less precise for callers and tests.

### Bounded execution surface
The initial API includes `step`, `runSteps`, and `runUntil`, but omits `runToCompletion`. `runUntil` processes events whose scheduled time is less than or equal to the limit, including newly emitted in-range events.

This keeps execution semantics explicit and avoids accidentally introducing non-terminating convenience behavior.

Alternatives considered:
- `runToCompletion`: attractive ergonomically, but risky for self-rescheduling workloads and less useful pedagogically than explicit bounds.

### JML boundary is selective, not total
The record types and straightforward simulator operations (`initialize`, `schedule`, `peekNextEvent`) should carry full JML contracts. `step` should get partial JML for key postconditions, while `runSteps` and `runUntil` rely primarily on tests because loop-heavy behavior and lambda invocation are likely to challenge OpenJML.

Alternatives considered:
- Full JML on everything: desirable in theory, but likely brittle and distracting in this workshop setting.
- No JML on the simulator API: would miss the educational value of showing where formal contracts help most.

## Risks / Trade-offs

- [OpenJML rejects some otherwise sensible code shapes] -> Keep the design record-centric, isolate complex logic, and allow `skipesc` only where verification friction outweighs teaching value.
- [Deterministic ordering depends on maintaining queue invariants] -> Validate state and emitted events aggressively, and cover ordering rules with unit and property tests.
- [Self-rescheduling actions can create non-terminating workloads] -> Expose only bounded execution APIs initially and document that `runUntil` continues processing newly emitted in-range events.
- [Generic payloads and actions may make tests verbose] -> Use simple workshop-oriented sample payloads in tests, such as a counter record.
- [The API may feel low-level for some use cases] -> Favor a small, principled core now; add higher-level helpers only after real usage appears.
