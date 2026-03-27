## 1. Core Types and API Surface

- [ ] 1.1 Create the `com.kevel.sim` package and add the public value types for simulation state, scheduled events, caller-facing event specs, action results, and action callbacks.
- [ ] 1.2 Implement constructor validation and invariant-preserving query methods for the value types, including non-null payloads, non-negative counters, and valid pending-event collections.
- [ ] 1.3 Implement deterministic event ordering and engine-owned sequencing so simultaneous events execute consistently without caller-supplied sequence numbers.

## 2. Simulator Operations

- [ ] 2.1 Implement `Simulator.initialize`, `schedule`, and `scheduleAll` with the documented null, time-boundary, and payload-preservation behavior.
- [ ] 2.2 Implement `Simulator.peekNextEvent` and `step` so one event is consumed at a time, time advances correctly, emitted events are validated, and malformed action results fail with `IllegalStateException`.
- [ ] 2.3 Implement `Simulator.runSteps` and `runUntil` with the specified bounded-execution semantics, including the inclusive `runUntil` boundary for newly emitted in-range events.

## 3. Contracts and Verification

- [ ] 3.1 Add JML invariants and constructor/postcondition contracts for the record types and pure query methods.
- [ ] 3.2 Add JML contracts for `Simulator.initialize`, `schedule`, and `peekNextEvent`, plus partial postconditions for `step` where OpenJML can handle them.
- [ ] 3.3 Document and apply any necessary OpenJML escape hatches such as `skipesc` only where verification limitations prevent practical checking.

## 4. Tests

- [ ] 4.1 Add unit tests covering initialization, scheduling, inspection, step execution, bounded execution, deterministic tie-breaking, and exception behavior.
- [ ] 4.2 Add property-based tests that generate valid states and operations to verify monotonic counters, queue ordering, invariant preservation, and equivalence between bounded helpers and repeated stepping.
- [ ] 4.3 Run the repository verification workflow and fix any issues so the new simulation library passes formatting, compilation, static analysis, tests, and JML checks that are intended to succeed.
