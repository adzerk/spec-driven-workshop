## 1. Core API and data model

- [x] 1.1 Create the `com.kevel.des` package and add core public record types: `EventId`, `Event<S>`, `ActionResult<S>`, `StepResult<S>`, and `RunResult<S>`.
- [x] 1.2 Implement `ActionResult<S>` as a record containing the new state and a list of event descriptors (time + action pairs) for declarative self-scheduling.
- [x] 1.3 Define `Event<S>` as an immutable record with time, sequence number, event identifier, and action fields, with ordering by `(time, sequence)`.
- [x] 1.4 Implement simulation creation (`Simulation.create(S)`) with initial time `0`, empty queue, and read-only accessors for current time, current state, queue emptiness, and `peekNext()`.

## 2. Scheduling and execution semantics

- [x] 2.1 Implement absolute-time and relative-delay scheduling (`scheduleAt`, `scheduleIn`) with validation for null actions, past times, negative delays, and `long` overflow. Return `EventId` from each.
- [x] 2.2 Implement `step()` to execute one earliest event by `(time, sequence)`, apply its action to get an `ActionResult`, update state, and insert any action-produced event descriptors into the queue with proper sequence numbering and time validation.
- [x] 2.3 Implement run variants: `run()` drains the queue; `runUntilTime(long)` stops at a time bound; `runUntil(Predicate)` evaluates the predicate before each step and stops without executing when satisfied. Each returns a `RunResult<S>`.

## 3. Contracts and documentation

- [x] 3.1 Add Javadoc describing preconditions, postconditions, invariants, and error behavior for all public API methods, including the `ActionResult` self-scheduling model.
- [x] 3.2 Document single-threaded usage expectations, the pure action contract (`S -> ActionResult<S>`), and the effect of impure actions on functional semantics.
- [x] 3.3 Add a minimal usage example demonstrating deterministic scheduling, state evolution, and self-scheduling via `ActionResult`.

## 4. JML specifications

- [x] 4.1 Add JML class-level invariants on `Simulation` for non-negative current time and non-negative sequence counter.
- [x] 4.2 Add JML `requires` and `ensures` clauses on `step()` expressing the non-empty queue precondition and time-advancement postcondition.
- [x] 4.3 Add JML `requires` clauses on at least one scheduling method (`scheduleAt` or `scheduleIn`) expressing time or delay validity.
- [x] 4.4 Verify that JML annotations parse without syntax errors under OpenJML. Use `skipesc`/`skiprac` on methods where full verification is impractical.

## 5. Validation

- [x] 5.1 Add unit tests for simulation creation: valid initial state, null rejection.
- [x] 5.2 Add unit tests for scheduling validation: null actions, past times, negative delays, overflow, FIFO ordering for equal-time events, scheduling at current time.
- [x] 5.3 Add unit tests for stepping: time and state update, empty queue rejection, step result contents.
- [x] 5.4 Add unit tests for self-scheduling via `ActionResult`: action-produced events enter the queue, invalid action-produced times are rejected, action-produced events integrate into global ordering.
- [x] 5.5 Add unit tests for run operations: drain to empty, run on empty queue, time-bounded run, predicate-bounded run with before-step evaluation, immediate predicate satisfaction, null predicate rejection, past time-bound rejection.
- [x] 5.6 Add unit tests for `peekNext()`: returns next event, empty on empty queue, non-mutating, repeated peeks return same event.
- [x] 5.7 Add unit tests for determinism: replaying the same schedule yields identical traces, determinism holds with self-scheduling actions.
- [x] 5.8 Add jqwik property tests for monotonic time, FIFO ordering, and deterministic replay across generated schedules.
- [x] 5.9 Add jqwik model-based stateful test comparing the real engine against a simple reference model.

## 6. Project verification

- [x] 6.1 Run `make format` and `make check` and fix any issues needed for the new library to pass the workshop toolchain.
- [x] 6.2 Run `make check-jml` to verify JML annotations parse and check without syntax errors for DES annotations, allowing known baseline OpenJML verification warnings outside this change scope.
