# Discrete Event Simulation Library — Implementation Plan

## Design Decisions

| Decision | Choice |
|---|---|
| Payload `S` | Always non-null; callers use `Void` or a unit record for stateless models |
| `runUntil` boundary | Events with `scheduledAt <= limitTime` execute; `> limitTime` remain |
| `runToCompletion` | Removed; callers compose `runSteps` / `runUntil` |
| Value types | Java records for `Event<S>`, `TransitionResult<S>`, `SimulationState<S>` |
| Bad actions | `step()` validates; throws `IllegalStateException` on malformed results |
| Query methods | `isEmpty()` and `pendingCount()` added to `SimulationState` |
| Null handling | `NullPointerException` for null arguments (via `Objects.requireNonNull`); `IllegalArgumentException` for domain violations (negative time, past scheduling) |
| Time representation | `long` discrete ticks; no `Instant`/`Duration` |
| Threading | Single-threaded by construction; no shared mutable global state, no internal concurrency primitives |

---

## Types and Responsibilities

### `SimulationState<S>` — record, the complete engine snapshot

**Fields:**

- `long currentTime`
- `long nextSequence`
- `long processedCount`
- `S payload`
- `List<Event<S>> pendingEvents`

**Representation invariants** (class-level JavaDoc + JML `invariant`):

- `currentTime >= 0`
- `nextSequence >= 0`
- `processedCount >= 0`
- `payload != null`
- `pendingEvents != null`, contains no nulls
- `pendingEvents` is sorted by `(scheduledAt, sequenceNumber)` ascending
- Every element has `scheduledAt >= currentTime`
- Every element has `sequenceNumber < nextSequence`
- All sequence numbers in `pendingEvents` are unique

Compact constructor validates all invariants; throws `IllegalArgumentException` on violation.

**Query methods:**

- `isEmpty()` — `/*@ pure @*/` — returns `pendingEvents.isEmpty()`
- `pendingCount()` — `/*@ pure @*/` — returns `pendingEvents.size()`

---

### `Event<S>` — record, a single scheduled transition

**Fields:**

- `long scheduledAt`
- `long sequenceNumber`
- `EventAction<S> action`

**Representation invariants:**

- `scheduledAt >= 0`
- `sequenceNumber >= 0`
- `action != null`

Natural ordering: compare by `scheduledAt`, then `sequenceNumber`. Implements `Comparable<Event<S>>`.

---

### `EventAction<S>` — `@FunctionalInterface`

Single method: `TransitionResult<S> execute(long currentTime, S payload)`

**Contract** (documented in JavaDoc; not JML-verifiable):

- Pre: `currentTime >= 0`, `payload != null`
- Post: result is non-null; `result.payload() != null`; every emitted event has `scheduledAt >= currentTime` and non-null action
- The engine validates this contract at the `step` call site

Note: the signature takes `currentTime` and `payload`, not the full `SimulationState`. This prevents actions from depending on queue internals or sequence counters.

Purity of user-supplied actions is the caller's responsibility, not an engine invariant. The engine is structurally functional (immutable state threading) while accepting that user-supplied actions may have side effects.

---

### `TransitionResult<S>` — record, output of an action

**Fields:**

- `S payload`
- `List<EventSpec<S>> emittedEvents`

**Representation invariants:**

- `payload != null`
- `emittedEvents != null`, contains no nulls
- Every element has `scheduledAt >= 0` and `action != null`

---

### `EventSpec<S>` — record, caller-facing event descriptor (no sequence number)

**Fields:**

- `long scheduledAt`
- `EventAction<S> action`

**Representation invariants:**

- `scheduledAt >= 0`
- `action != null`

This separates the caller's concern (when and what) from the engine's concern (ordering via sequence number). Without this type, callers would need to fabricate sequence numbers.

---

### `Simulator` — final class, private constructor, static methods only

| Method | Pre | Post |
|---|---|---|
| `initialize(long time, S payload)` | `time >= 0`, `payload != null` | Empty queue, `processedCount == 0`, `nextSequence == 0`, all invariants established |
| `schedule(SimulationState<S>, EventSpec<S>)` | Both non-null; `spec.scheduledAt >= state.currentTime` | Queue size = old + 1; payload unchanged; sequence number assigned from `nextSequence`; `nextSequence` incremented; ordering maintained |
| `scheduleAll(SimulationState<S>, List<EventSpec<S>>)` | Both non-null; list contains no nulls; each spec has `scheduledAt >= state.currentTime` | Queue size = old + list.size(); sequence numbers assigned consecutively in list order; payload unchanged. Empty list is a no-op returning equal state. |
| `peekNextEvent(SimulationState<S>)` | State non-null | Returns `Optional<Event<S>>`; present iff `!isEmpty()`; if present, is the minimum element by event ordering; state not consumed or modified |
| `step(SimulationState<S>)` | State non-null; `!state.isEmpty()` | Exactly one event consumed (the minimum); `result.currentTime == consumed.scheduledAt`; `result.currentTime >= input.currentTime`; `result.processedCount == input.processedCount + 1`; emitted events validated and merged; throws `IllegalStateException` if action returns malformed result |
| `runSteps(SimulationState<S>, long maxSteps)` | State non-null; `maxSteps >= 0` | Processes `min(maxSteps, available events)` steps; equivalent to repeated `step`; all invariants preserved at each intermediate and final state |
| `runUntil(SimulationState<S>, long limitTime)` | State non-null; `limitTime >= state.currentTime` | All events with `scheduledAt <= limitTime` are processed (including newly emitted ones that fall within range); remaining events have `scheduledAt > limitTime`; `result.currentTime <= limitTime` unless queue was empty before limit (then `currentTime` is time of last processed event or unchanged) |

**Exception policy:**

- `NullPointerException` for null arguments (via `Objects.requireNonNull`)
- `IllegalArgumentException` for domain violations (negative time, past scheduling)
- `IllegalStateException` for violated action contracts (malformed `TransitionResult` from `step`)

---

## JML Strategy

- **Full JML contracts on:** `SimulationState`, `Event`, `EventSpec`, `TransitionResult` (all records — invariants + constructor postconditions + `pure` query methods).
- **Full JML contracts on:** `Simulator.initialize`, `Simulator.schedule`, `Simulator.peekNextEvent`.
- **Partial JML on:** `Simulator.step` — specify pre/post for counts, time, queue size; use `skipesc` if OpenJML struggles with the action invocation or generic lambda internals.
- **Skip JML on:** `EventAction` (functional interface; OpenJML can't verify lambda bodies), `Simulator.runSteps`, `Simulator.runUntil` (loop-based; rely on tests instead).
- Document the JML boundary explicitly in class-level JavaDoc on `Simulator`.
- `pure` applies to the logical contract; `skipesc` may be needed on methods with internal temporary allocation that OpenJML rejects.

---

## Testing Plan

### Unit Tests (`SimulatorTest.java`)

One or more tests per contract clause.

#### Precondition Violation Tests

- `initialize` with negative time → `IllegalArgumentException`
- `initialize` with null payload → `NullPointerException`
- `schedule` with null state or null spec → `NullPointerException`
- `schedule` with `spec.scheduledAt < state.currentTime` → `IllegalArgumentException`
- `scheduleAll` with null elements in list → `NullPointerException`
- `step` on empty queue → `IllegalStateException`
- `runSteps` with negative `maxSteps` → `IllegalArgumentException`
- `runUntil` with `limitTime < currentTime` → `IllegalArgumentException`

#### Postcondition Tests

- `initialize` returns correct time, zero counts, empty queue
- `schedule` increments queue size by 1, preserves payload, assigns correct sequence number
- `scheduleAll` increments by list size, assigns consecutive sequences in list order
- `scheduleAll` with empty list returns equal state
- `peekNextEvent` on empty queue returns empty `Optional`
- `peekNextEvent` on non-empty returns minimum event; repeated calls return same event
- `step` consumes exactly one event, sets time to that event's time, increments `processedCount`
- `step` merges emitted events from action into queue in correct order
- `step` with action returning malformed result (null, past events) → `IllegalStateException`
- `runSteps(0)` is a no-op
- `runSteps(n)` on queue with fewer than n events processes all and stops
- `runUntil` processes events at exactly `limitTime` (boundary test)
- `runUntil` leaves events strictly after `limitTime` in queue

#### Invariant Verification Tests

After every operation, verify:

- Time non-negative
- Counts non-negative
- Queue sorted
- No past events in queue
- Sequence numbers unique and `< nextSequence`
- Payload non-null

#### Determinism Tests

- Same initial state + same events → same result (event ordering is total)
- Tie-breaking: two events at same time execute in sequence-number order

---

### Property-Based Tests (`SimulatorProperties.java`)

Uses jqwik.

#### Generators

- `Arbitrary<SimulationState<S>>` — valid states with arbitrary queue contents (using a simple payload type like `record Counter(int n)`)
- `Arbitrary<List<EventSpec<S>>>` — valid event specs with times >= some floor
- `Arbitrary<EventAction<S>>` — simple deterministic actions (increment counter, emit 0–2 follow-up events at future times, no-op)

#### State Transition Coverage

- Generate random sequences of `schedule`, `scheduleAll`, `step`, `runSteps`, `runUntil` operations and verify invariants hold after every step
- Compare `runSteps(n)` to `n` iterated `step` calls — results are identical (with same rule: stop early if queue empties)
- Compare `runUntil(t)` to iterated `step` with `peekNextEvent` guard — results are identical

#### Global Invariants (checked after every generated operation)

- `currentTime >= 0` and monotonically non-decreasing across operations
- `processedCount >= 0` and monotonically non-decreasing
- `nextSequence >= 0` and monotonically non-decreasing
- Queue is sorted by `(scheduledAt, sequenceNumber)`
- All pending events have `scheduledAt >= currentTime`
- All sequence numbers in queue are unique and `< nextSequence`
- Payload is non-null

#### Safety Properties

- No event ever executes before its `scheduledAt` time
- Execution order is deterministic: given identical inputs, output states are `equals()`
- Payload changes only through event action return values — `schedule`/`scheduleAll`/`peekNextEvent` never alter payload
- `runSteps(n)` processes at most `n` events (`processedCount` delta <= n)

#### Bounded Liveness Properties

These are bounded approximations of liveness properties — true liveness requires proof, not testing.

- If N events are scheduled and every action emits zero new events, `runSteps(N)` produces an empty queue (complete drainage)
- If all emitted events are scheduled strictly after current time and each action emits at most k < 1.0 events on average, `runSteps(bound)` for a sufficient bound drains the queue
- `runUntil(t)` always terminates and leaves no events with `scheduledAt <= t`

---

## File Layout

```
src/main/java/com/kevel/sim/
    SimulationState.java
    Event.java
    EventSpec.java
    EventAction.java
    TransitionResult.java
    Simulator.java

src/test/java/com/kevel/sim/
    SimulatorTest.java         (unit tests)
    SimulatorProperties.java   (jqwik property tests)
```
