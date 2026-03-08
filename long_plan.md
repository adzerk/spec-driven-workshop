# Long Plan: Simple Discrete-Event Simulation (DES) Library (Java 21)

This document defines a minimal, single-threaded discrete-event simulation library with a functional core. It specifies the API, execution semantics, constraints, invariants, and a comprehensive validation strategy (JUnit 5 + jqwik).

## 1) Goals and Non-Goals

Goals:
- Single-threaded DES engine (no concurrency).
- Deterministic execution.
- Time represented as `long` ticks (unit is user-defined).
- Functional core: event effects are expressed as `S -> S` transitions.
- Clear and explicit contracts: preconditions, postconditions, invariants.
- Traceable execution: all `step()` results are captured in `RunResult.stepResults`.

Non-goals (v1):
- Event cancellation.
- Parallel execution.
- Real-time pacing.
- Multiple event priorities beyond time + FIFO tie-break.
- Persistence/replay formats.
- Performance optimizations beyond straightforward `PriorityQueue` behavior.

## 2) Key Design Principles

- Determinism first: ordering and tie-breaking are fully defined.
- Pure model evolution: `action` is a state transition function (recommended pure).
- Minimal surface area: schedule events, step, run.
- Fail fast: invalid schedules (past events, negative delay, overflow) throw.
- Make invariants testable: ensure observable behavior enforces invariants.

## 3) Time Model

- Simulation time is `long` ticks.
- Tick unit is defined by the user/domain (e.g., milliseconds, minutes, abstract steps).
- All scheduled events are placed at integral ticks.

## 4) Functional Core Contract (User Guidance)

The engine is deterministic regardless of whether user-provided actions have side effects; however, to preserve functional semantics and reproducibility:

- Prefer immutable state `S` (e.g., Java records; persistent collections).
- Treat `S` as immutable-by-convention if deep immutability is not enforced.
- Event actions SHOULD be referentially transparent:
  - no I/O
  - no reading wall-clock time
  - no randomness unless seeded and explicitly modeled
  - no mutation of captured external state

If side effects are needed, model them as data:
- Include an "effect log" within `S` (e.g., `List<Effect>`), return a new state with appended effects.
- Interpret effects outside the simulation engine.

## 5) Proposed Public API

Recommended package: `com.kevel.des`.

### 5.1) Core Types

Identifiers:
- `record EventId(long value)`

Events (internal ordering is by time + sequence):
- `record Event<S>(long time, long sequence, EventId id, UnaryOperator<S> action)`

Step trace element:
- `record StepResult<S>(long previousTime, long newTime, EventId executedEventId, S previousState, S newState)`

Run result (includes full trace):
- `record RunResult<S>(long stepsExecuted, long startTime, long endTime, S finalState, boolean stoppedByCondition, List<StepResult<S>> stepResults)`

### 5.2) Simulation Engine

`final class Simulation<S>`

Construction:
- `static <S> Simulation<S> create(S initialState)`

Observers:
- `long now()`
- `S state()`
- `boolean isEmpty()`
- `Optional<Event<S>> peekNext()`

Scheduling:
- `EventId scheduleAt(long time, UnaryOperator<S> action)`
- `EventId scheduleIn(long delay, UnaryOperator<S> action)`

Execution:
- `StepResult<S> step()`
- `RunResult<S> run()`
- `RunResult<S> runUntilTime(long tEnd)`
- `RunResult<S> runUntil(Predicate<Simulation<S>> stopCondition)`

Notes:
- The engine may mutate its internal queue and `state` field, but its behavior is a deterministic interpreter of scheduled transitions.
- `Event` is a value type; if exposed from `peekNext`, it is observational (should not allow modification).

## 6) Core Implementation Plan

### 6.1) Data Structures

- Pending events: `PriorityQueue<Event<S>>` ordered by `(time asc, sequence asc)`.
- Tie-breaker sequence: `long nextSequence` incremented on every schedule call.
- Event id counter: `long nextEventId` incremented on every schedule call.

Comparator definition (conceptual):
- Compare `Event.time` first.
- If equal, compare `Event.sequence`.

### 6.2) Scheduling Semantics

`scheduleAt(time, action)`:
- Validates `action` and time.
- Assigns `sequence = nextSequence++`.
- Assigns `id = new EventId(nextEventId++)`.
- Adds event to queue.
- Returns `id`.

`scheduleIn(delay, action)`:
- Computes `time = Math.addExact(now, delay)`.
- Delegates to `scheduleAt(time, action)`.

### 6.3) Execution Semantics

`step()`:
- Pops the minimum event.
- Sets `previousTime = now`.
- Sets `now = event.time`.
- Computes `newState = event.action.apply(previousState)`.
- Updates internal `state`.
- Returns `StepResult(previousTime, now, event.id, previousState, newState)`.

`run()`:
- `startTime = now`.
- While queue not empty, call `step()` and append each `StepResult` to a local list.
- Return `RunResult(stepsExecuted, startTime, endTime, finalState, false, unmodifiableSnapshot(stepResults))`.

`runUntilTime(tEnd)`:
- While queue not empty and `peek.time <= tEnd`, call `step()`.

`runUntil(stopCondition)`:
- While queue not empty and stop condition is false, call `step()`.
- If stopped due to condition, set `stoppedByCondition = true`.

Implementation note:
- `RunResult.stepResults` SHOULD be an unmodifiable snapshot via `List.copyOf(collected)` to avoid exposing internal mutable lists.

## 7) Contracts (Preconditions, Postconditions, Invariants)

All contracts below are normative for v1.

### 7.1) Global Invariants (Simulation)

These invariants must hold after `create()` and after every public method that returns normally:

- Single-threaded use only: the instance is not thread-safe.
- `now` monotonic: `now` never decreases.
- No past events: every pending event `e` satisfies `e.time >= now`.
- Total order: events are ordered by `(time, sequence)`; `(time, sequence)` uniquely determines execution order.
- Sequence monotone: `sequence` assigned at scheduling is strictly increasing.

### 7.2) `create(initialState)`

Preconditions:
- `initialState != null`

Postconditions:
- `now() == 0`
- `state()` equals `initialState`
- queue empty: `isEmpty() == true` and `peekNext().isEmpty()`
- global invariants hold

Failure behavior:
- Throw `NullPointerException` if `initialState` is null.

### 7.3) `scheduleAt(time, action)`

Preconditions:
- `action != null`
- `time >= now()`

Postconditions:
- Returns non-null `EventId`.
- Exactly one event is enqueued.
- Enqueued event has:
  - `event.time == time`
  - `event.action == action`
  - unique `event.id`
  - `event.sequence` strictly greater than any previously scheduled event's sequence
- Queue size increases by 1.
- Global invariants hold.

Failure behavior:
- Throw `NullPointerException` if `action` is null.
- Throw `IllegalArgumentException` if `time < now()`.

### 7.4) `scheduleIn(delay, action)`

Preconditions:
- `action != null`
- `delay >= 0`
- `now() + delay` must not overflow `long`.

Postconditions:
- Equivalent to `scheduleAt(now + delay, action)`.
- Global invariants hold.

Failure behavior:
- Throw `NullPointerException` if `action` is null.
- Throw `IllegalArgumentException` if `delay < 0`.
- Throw `ArithmeticException` if `Math.addExact(now, delay)` overflows.

### 7.5) `peekNext()`

Preconditions: none.

Postconditions:
- Returns empty iff queue empty.
- If present, returned event has minimal `(time, sequence)` among queued events.
- Does not modify `now`, `state`, or queue contents.

### 7.6) `step()`

Preconditions:
- queue not empty.

Postconditions:
- Exactly one event is removed and executed; it is the minimum by `(time, sequence)`.
- `now()` becomes the executed event's time.
- `state()` becomes `event.action.apply(previousState)`.
- Returned `StepResult` accurately reflects the transition.
- Global invariants hold.

Failure behavior:
- Throw `IllegalStateException` if queue is empty.

### 7.7) `run()`

Preconditions: none.

Postconditions:
- Executes until queue is empty.
- `stoppedByCondition == false`.
- `startTime` equals `now` at entry; `endTime` equals `now` at exit.
- `finalState` equals `state` at exit.
- Trace is complete and consistent (see Run trace invariants).
- `stepResults` is non-null and unmodifiable.
- Global invariants hold.

### 7.8) `runUntilTime(tEnd)`

Preconditions:
- `tEnd >= now()`.

Postconditions:
- Executes exactly those events with `event.time <= tEnd`.
- If queue non-empty on return, `peekNext().get().time > tEnd`.
- `RunResult` fields/trace invariants hold.
- Global invariants hold.

Failure behavior:
- Throw `IllegalArgumentException` if `tEnd < now()`.

### 7.9) `runUntil(stopCondition)`

Preconditions:
- `stopCondition != null`.

Postconditions:
- Stops if queue empty or stop condition becomes true.
- `stoppedByCondition == true` iff it stopped due to stop condition.
- `RunResult` fields/trace invariants hold.
- Global invariants hold.

Failure behavior:
- Throw `NullPointerException` if `stopCondition` is null.

### 7.10) Run Trace Invariants (RunResult)

The returned trace must satisfy:

- `stepResults.size() == stepsExecuted`.
- Execution order: `stepResults` is in the same order as repeated `step()` calls.
- Time monotonicity: for each adjacent pair, `stepResults[i].newTime <= stepResults[i+1].newTime`.
- Final consistency:
  - If non-empty trace: `finalState.equals(last.newState())` and `endTime == last.newTime()`.
  - If empty trace: `finalState` equals state at run entry and `endTime == startTime`.

## 8) Error Handling Policy

Recommended (and tested) exceptions:
- `NullPointerException`: null `initialState`, null `action`, null `stopCondition`.
- `IllegalArgumentException`: schedule in the past; negative delay; `tEnd < now`.
- `IllegalStateException`: `step()` on empty queue.
- `ArithmeticException`: `scheduleIn` overflow via `Math.addExact`.

## 9) Performance and Memory Notes

- Scheduling and stepping are `O(log n)` due to priority queue.
- Capturing full trace is `O(k)` memory where `k` is the number of executed steps.
- v1 always returns the full trace in `RunResult.stepResults`; future extension could add tracing options (off/bounded/streamed).

## 10) Validation Plan

This library is small enough that tests are part of the spec.

### 10.1) JUnit 5 Unit Tests (Example-Based)

Create tests for:

Construction:
- `create(null)` throws `NullPointerException`.
- `create(s0)` => `now()==0`, `state()==s0`, `isEmpty()==true`, `peekNext().isEmpty()`.

Scheduling constraints:
- `scheduleAt(time < now, action)` throws `IllegalArgumentException`.
- `scheduleIn(delay < 0, action)` throws `IllegalArgumentException`.
- null `action` throws `NullPointerException`.
- `scheduleIn` overflow throws `ArithmeticException`.

Ordering/tie-breaking:
- Lower `time` executes first.
- Same `time` executes FIFO by schedule order.
- `peekNext()` is non-mutating (same answer until a `step()`; does not change time/state).

Step invariants:
- `step()` on empty queue throws `IllegalStateException`.
- After step: `now` is non-decreasing; `now==executedEvent.time`; state equals `action.apply(previousState)`.

Run semantics + trace:
- `run()` drains the queue.
- `runUntilTime(tEnd)` executes only events with `time <= tEnd`.
- Trace invariants: `stepsExecuted == stepResults.size()`; times monotone; `finalState` matches last `StepResult.newState()` when non-empty.

Determinism smoke test:
- Two fresh simulations with same initial state and same scheduling calls produce identical final state and same trace length (and ideally same times).

### 10.2) jqwik Property-Based Testing (Model-Based State Machine)

Approach: compare the SUT (real `Simulation`) against a pure reference model that implements the spec.

Reference model (pure):
- Fields: `long now`, `S state`, `long nextSeq`, and a pending-event structure ordered by `(time, seq)`.
- Use a deterministic structure such as:
  - `TreeMap<Long, ArrayList<RefEvent>>` where `RefEvent` contains `seq` and `UnaryOperator<S>`.
  - Ordering: smallest time, then list order (which is schedule FIFO).

Generate operation sequences (stateful testing):
- Operations: `scheduleAt`, `scheduleIn`, `peekNext`, `step`, `runUntilTime` (optional `run`).
- For valid sequences, generate only inputs that satisfy preconditions (except for dedicated negative tests).
- For each operation, apply it to both model and SUT.

Assertions after each operation:
- `sut.now() == model.now`.
- `sut.state().equals(model.state)` (use an immutable test state with structural equality).
- `sut.isEmpty() == model.queueEmpty`.
- `peekNext()` presence matches; if present, `peekNext().time == model.nextEventTime`.

Run trace equivalence (key property):
- For `run*()`, compare `RunResult.stepResults` to the model-produced list:
  - same length
  - for each step `i`: equal `newTime` and equal `newState` (and optionally `previousTime/previousState`)
- Also assert `stepsExecuted == stepResults.size()` and final consistency for every generated run.

Negative properties (exceptions):
- For generated invalid inputs:
  - `scheduleAt(time < now)` always throws `IllegalArgumentException`.
  - `scheduleIn(delay < 0)` always throws `IllegalArgumentException`.
  - overflow in `scheduleIn` always throws `ArithmeticException`.

Determinism property:
- Generate a schedule (list of schedule commands and actions) and replay it twice on fresh simulations.
- Assert identical traces (times and states) and identical final states.

## 11) Package / File Layout Plan

Recommended v1 file layout:
- `src/main/java/com/kevel/des/EventId.java`
- `src/main/java/com/kevel/des/Event.java`
- `src/main/java/com/kevel/des/StepResult.java`
- `src/main/java/com/kevel/des/RunResult.java`
- `src/main/java/com/kevel/des/Simulation.java`

Tests:
- `src/test/java/com/kevel/des/SimulationTest.java` (JUnit 5)
- `src/test/java/com/kevel/des/SimulationPropertiesTest.java` (jqwik)

## 12) Future Extensions (Not in v1)

- Cancellation (`EventId` -> cancel) and re-scheduling.
- Event payloads, typed actions, and richer tracing.
- Multiple queues/priorities.
- Configurable tracing (off/bounded/streaming consumer).
- Convenience DSL for common DES patterns (processes, resources, distributions).
