# DES Library Plan (Java 21, long ticks)

## Scope
- Single-threaded discrete-event simulation (DES) engine.
- Time is `long` ticks (unit is user-defined).
- Deterministic: same initial state + same scheduling calls in the same order => identical results.
- Functional core: each event carries a state transition `S -> S` (recommended pure; see contract).

## Proposed API
Package: `com.kevel.des`.

Types:
- `record EventId(long value)`
- `record Event<S>(long time, long sequence, EventId id, UnaryOperator<S> action)`
- `record StepResult<S>(long previousTime, long newTime, EventId executedEventId, S previousState, S newState)`
- `record RunResult<S>(long stepsExecuted, long startTime, long endTime, S finalState, boolean stoppedByCondition, List<StepResult<S>> stepResults)`

Engine: `final class Simulation<S>`
- `static <S> Simulation<S> create(S initialState)`
- Observers: `long now()`, `S state()`, `boolean isEmpty()`, `Optional<Event<S>> peekNext()`
- Scheduling: `EventId scheduleAt(long time, UnaryOperator<S> action)`, `EventId scheduleIn(long delay, UnaryOperator<S> action)`
- Execution: `StepResult<S> step()`, `RunResult<S> run()`, `RunResult<S> runUntilTime(long tEnd)`, `RunResult<S> runUntil(Predicate<Simulation<S>> stopCondition)`

## Core Algorithm
- Maintain a `PriorityQueue<Event<S>>` ordered by `(time asc, sequence asc)`.
- `sequence` is a strictly increasing counter assigned at scheduling time to guarantee FIFO for equal `time`.
- `step()` pops the minimum event, sets `now=event.time`, computes `state=event.action.apply(previousState)`, and returns a `StepResult`.
- `run*()` repeatedly calls `step()` while not stopped; it collects the returned `StepResult`s into `RunResult.stepResults`.

## Contracts

### Global Invariants (Simulation)
- Single-threaded use only (not thread-safe; no concurrent calls).
- `now` is monotonically non-decreasing.
- No past events: every queued event `e` satisfies `e.time >= now`.
- Total order: events are uniquely ordered by `(time, sequence)`.
- `sequence` is strictly increasing for each scheduled event within a `Simulation` instance.

### Functional Core (User Contract)
- `S` should be immutable-by-convention (records/immutable structures preferred).
- `action` should be referentially transparent (no I/O, no wall-clock reads, no mutation of captured external state).
- If effects are needed, encode them in state (e.g., effect log) and interpret outside the engine.

### `create(initialState)`
Preconditions:
- `initialState != null`

Postconditions:
- `now() == 0`
- `state()` equals `initialState`
- queue empty (`isEmpty() == true` and `peekNext().isEmpty()`)

### `scheduleAt(time, action)`
Preconditions:
- `action != null`
- `time >= now()`

Postconditions:
- returns a non-null `EventId`
- exactly one event is enqueued with `event.time == time`
- enqueued event has unique `(time, sequence, id)`; `sequence` is greater than all previously assigned sequences
- queue size increases by 1

Failures:
- `NullPointerException` if `action` is null
- `IllegalArgumentException` if `time < now()`

### `scheduleIn(delay, action)`
Preconditions:
- `action != null`
- `delay >= 0`
- `now() + delay` must not overflow `long` (enforced with `Math.addExact`)

Postconditions:
- equivalent to `scheduleAt(now() + delay, action)`

Failures:
- `NullPointerException` if `action` is null
- `IllegalArgumentException` if `delay < 0`
- `ArithmeticException` if `Math.addExact(now(), delay)` overflows

### `peekNext()`
Preconditions: none

Postconditions:
- empty iff queue empty
- does not mutate `now`, `state`, or the queue

### `step()`
Preconditions:
- queue not empty

Postconditions:
- removes and executes exactly one event: the minimum by `(time, sequence)`
- `now()` becomes the executed event's `time`
- `state()` becomes `event.action.apply(previousState)`
- preserves all global invariants

Failures:
- `IllegalStateException` if queue is empty

### `run()`
Preconditions: none

Postconditions:
- executes until the queue is empty
- `stoppedByCondition == false`
- `startTime` equals `now` at entry; `endTime` equals `now` at exit
- `finalState` equals `state` at exit
- `stepResults` is non-null and an unmodifiable snapshot
- preserves all global invariants

### `runUntilTime(tEnd)`
Preconditions:
- `tEnd >= now()`

Postconditions:
- executes exactly events with `event.time <= tEnd`
- if queue non-empty on return, `peekNext().get().time > tEnd`
- `RunResult` fields/tracing rules match `run()`

Failures:
- `IllegalArgumentException` if `tEnd < now()`

### `runUntil(stopCondition)`
Preconditions:
- `stopCondition != null`

Postconditions:
- stops when queue becomes empty or `stopCondition.test(this)` becomes true
- `stoppedByCondition` is true iff stopped due to the condition
- `RunResult` fields/tracing rules match `run()`

Failures:
- `NullPointerException` if `stopCondition` is null

### Run Trace Invariants (RunResult)
- `stepResults.size() == stepsExecuted`
- `stepResults` are in execution order
- Time monotonic across trace: for adjacent steps, `newTime` does not decrease
- Final consistency:
  - if `stepResults` non-empty: `finalState.equals(last.newState())` and `endTime == last.newTime()`
  - if `stepResults` empty: `finalState` equals the state at run entry and `endTime == startTime`

## Validation Plan

### JUnit 5 (example-based)
- Construction: null initial state; initial observers (`now`, `state`, `isEmpty`, `peekNext`).
- Constraints: reject scheduling in the past, negative delay, null action; overflow behavior for `scheduleIn`.
- Ordering: increasing time; same-time FIFO (sequence tie-breaker); `peekNext()` is non-mutating.
- Stepping invariants: `step()` on empty fails; `now` monotone; state transition matches `action`.
- Run semantics + trace: `run()` drains; `runUntilTime()` respects `tEnd`; trace invariants (`stepsExecuted`, monotone times, final consistency).
- Determinism smoke test: identical schedule => identical final state and trace length.

### jqwik (property-based, model-based state machine)
- Build a pure reference model with `now`, `state`, `nextSeq`, and pending events ordered by `(time, seq)`.
- Generate valid operation sequences (stateful testing): scheduleAt, scheduleIn, peekNext, step, runUntilTime (and optional run).
- After each operation, assert SUT matches model: `now`, `state`, emptiness, next event time.
- For `run*()`, compare the full returned trace (`RunResult.stepResults`) to the model-produced trace (times and states per step).
- Negative properties: invalid inputs always throw expected exceptions (past scheduling, negative delay, overflow).
- Determinism property: replay same generated schedule twice on fresh sims => identical traces and final states.
