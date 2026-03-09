## 1. Core API and data model

- [ ] 1.1 Create the `com.kevel.des` package and add the core public types for simulation state, event identity, and execution results.
- [ ] 1.2 Define the event representation and ordering strategy based on `(time, sequence)` using `long` ticks.
- [ ] 1.3 Implement simulation creation and read-only accessors for current time, current state, queue emptiness, and next-event inspection.

## 2. Scheduling and execution semantics

- [ ] 2.1 Implement absolute-time and relative-delay scheduling with validation for null actions, past times, negative delays, and overflow.
- [ ] 2.2 Implement `step()` so it executes exactly one earliest event, advances time, updates state, and rejects execution on an empty queue.
- [ ] 2.3 Implement run variants for draining the queue, stopping at a time bound, and stopping on a predicate with documented boundary semantics.

## 3. Contracts and documentation

- [ ] 3.1 Add Javadoc describing preconditions, postconditions, invariants, and single-threaded usage expectations for the public API.
- [ ] 3.2 Document the functional-style expectation that event actions behave like pure state transitions and explain the trade-offs of mutable state or side effects.
- [ ] 3.3 Add a minimal usage example that demonstrates deterministic scheduling and state evolution.

## 4. Validation

- [ ] 4.1 Add unit tests for simulation creation, scheduling validation, FIFO ordering for equal-time events, stepping behavior, and run boundaries.
- [ ] 4.2 Add tests for overflow and other error cases to verify the documented failure behavior.
- [ ] 4.3 Add jqwik property or model-based tests that validate determinism, monotonic time, and equivalence against a simple reference model.

## 5. Project verification

- [ ] 5.1 Run the relevant formatting and test/check commands and fix any issues needed for the new library to pass the workshop toolchain.
