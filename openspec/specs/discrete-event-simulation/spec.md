### Requirement: Simulation creation establishes an initial deterministic state
The library SHALL provide a way to create a simulation with a non-null initial state. A newly created simulation MUST start at time `0`, MUST contain no pending events, and MUST expose the provided initial state as its current state.

#### Scenario: Create simulation with valid initial state
- **WHEN** a caller creates a simulation with a non-null initial state
- **THEN** the simulation starts at time `0`, has an empty event queue, and reports the provided value as the current state

#### Scenario: Reject null initial state
- **WHEN** a caller creates a simulation with a null initial state
- **THEN** the library throws `NullPointerException`

### Requirement: Scheduling preserves future-only event ordering and returns an event identifier
The library SHALL allow callers to schedule events at an absolute time or relative delay. Each scheduling operation MUST accept a non-null action representing a pure state transition. Scheduled events MUST have execution times greater than or equal to the simulation's current time. Events scheduled for the same time MUST execute in the order they were scheduled. Each scheduling operation MUST return a non-null `EventId` that uniquely identifies the scheduled event within the simulation instance.

#### Scenario: Schedule an event at a future absolute time
- **WHEN** a caller schedules an event at a time greater than or equal to the current simulation time with a non-null action
- **THEN** the event is added to the pending queue for that exact time and a non-null `EventId` is returned

#### Scenario: Schedule an event at the current simulation time
- **WHEN** a caller schedules an event at a time exactly equal to the current simulation time
- **THEN** the event is added to the pending queue at that time and executes after any previously scheduled events at the same time

#### Scenario: Schedule an event by relative delay
- **WHEN** a caller schedules an event with a non-negative delay and a non-null action
- **THEN** the event is added at the current simulation time plus that delay and a non-null `EventId` is returned

#### Scenario: Reject scheduling in the past
- **WHEN** a caller attempts to schedule an event at a time earlier than the current simulation time
- **THEN** the library throws `IllegalArgumentException`

#### Scenario: Preserve FIFO ordering for equal-time events
- **WHEN** multiple events are scheduled for the same execution time
- **THEN** those events execute in the order they were originally scheduled

#### Scenario: Reject null action in absolute-time scheduling
- **WHEN** a caller schedules an event at any time with a null action
- **THEN** the library throws `NullPointerException`

#### Scenario: Reject null action in relative-delay scheduling
- **WHEN** a caller schedules an event with any delay and a null action
- **THEN** the library throws `NullPointerException`

#### Scenario: Reject negative relative delay
- **WHEN** a caller schedules an event with a negative delay
- **THEN** the library throws `IllegalArgumentException`

#### Scenario: Reject overflow when computing delayed time
- **WHEN** a caller schedules an event with a delay that causes the sum of current time and delay to overflow `long`
- **THEN** the library throws `ArithmeticException`

### Requirement: Event actions are pure functions that may produce new events
Event actions MUST be pure functions of type `S -> ActionResult<S>`, where `ActionResult` bundles the new state together with zero or more new event descriptors to schedule. The engine MUST interpret these descriptors after applying the action, inserting them into the pending queue with proper time validation and sequence ordering. Actions MUST NOT interact with the simulation engine directly; all scheduling intent MUST be expressed declaratively through the returned result.

#### Scenario: Action returns new state with no new events
- **WHEN** an event action returns an `ActionResult` containing a new state and an empty list of new event descriptors
- **THEN** the engine updates the simulation state to the new state and does not add any events to the queue

#### Scenario: Action returns new state with new events to schedule
- **WHEN** an event action returns an `ActionResult` containing a new state and one or more event descriptors with valid future times
- **THEN** the engine updates the simulation state to the new state and adds each described event to the pending queue at the specified time, with sequence numbers preserving the descriptor order

#### Scenario: Action-produced event with time before current simulation time is rejected
- **WHEN** an event action returns an `ActionResult` containing an event descriptor with a time earlier than the current simulation time
- **THEN** the engine rejects the step with `IllegalArgumentException`

#### Scenario: Action-produced events integrate into global ordering
- **WHEN** an event action produces new event descriptors and there are already pending events in the queue
- **THEN** all events (pre-existing and newly produced) are ordered by `(time, sequence)` with the newly produced events receiving sequence numbers that place them after all events scheduled before the step

### Requirement: Event queue is value-oriented
The event queue MUST behave as a value-oriented data structure. Events stored in the queue MUST be immutable records containing time, sequence number, event identifier, and the action. The queue's ordering MUST be determined entirely by `(time, sequence)` pairs using a strict total order. The sequence number MUST be a monotonically increasing value assigned at the point of scheduling, whether the event was scheduled externally by the caller or produced by an action result.

#### Scenario: Events are immutable records
- **WHEN** an event is added to the queue through any scheduling path
- **THEN** the event is represented as an immutable record with time, sequence, identifier, and action fields

#### Scenario: Sequence numbers increase monotonically across all scheduling sources
- **WHEN** events are scheduled by a mix of external caller operations and action-produced descriptors
- **THEN** every event receives a sequence number strictly greater than all previously assigned sequence numbers within that simulation instance

### Requirement: Stepping executes exactly one next event and processes produced events
The library SHALL provide a step operation that executes exactly one pending event, chosen by the minimum `(time, sequence)` pair. Executing a step MUST advance simulation time to that event's time, apply the event action to the previous state to obtain an `ActionResult`, update the state to the result's new state, insert any events described in the result into the pending queue, and preserve all simulation invariants. The step operation MUST return a result record containing the previous time, new time, executed event identifier, previous state, and new state.

#### Scenario: Step executes the earliest scheduled event
- **WHEN** a caller steps a simulation that has pending events
- **THEN** the earliest event by `(time, sequence)` is executed and removed from the queue

#### Scenario: Step updates time and state from action result
- **WHEN** a caller steps a simulation with a pending event whose action returns an `ActionResult`
- **THEN** the simulation time becomes the event time, the simulation state becomes the `ActionResult`'s new state, and any event descriptors in the result are added to the queue

#### Scenario: Step returns a result record with before and after state
- **WHEN** a caller steps a simulation
- **THEN** the returned result contains the previous time, new time, executed event identifier, previous state, and new state

#### Scenario: Reject step on empty queue
- **WHEN** a caller steps a simulation with no pending events
- **THEN** the library throws `IllegalStateException`

### Requirement: Observing the next pending event does not modify the simulation
The library SHALL provide a `peekNext()` operation that returns the next pending event without modifying the simulation's time, state, or queue. The operation MUST return an `Optional` containing the event record if events are pending, or empty if the queue is empty.

#### Scenario: Peek returns the next event without side effects
- **WHEN** a caller peeks at the next event on a simulation with pending events
- **THEN** the library returns the event that would be executed by the next step, and the simulation's time, state, and queue are unchanged

#### Scenario: Peek on empty queue returns empty
- **WHEN** a caller peeks at the next event on a simulation with no pending events
- **THEN** the library returns an empty `Optional`

#### Scenario: Repeated peeks return the same event
- **WHEN** a caller peeks multiple times without stepping
- **THEN** each peek returns the same event

### Requirement: Run operations stop at defined boundaries
The library SHALL provide run operations for draining all pending events, stopping at a time boundary, and stopping when a predicate indicates completion. Each run variant MUST execute zero or more valid steps and MUST stop only at the boundary defined by that variant. The stopping predicate for `runUntil` MUST be evaluated before each step; if the predicate is satisfied before the first step, zero steps are executed. Each run operation MUST return a result record containing the number of steps executed, start time, end time, final state, and whether the run was stopped by a condition.

#### Scenario: Run drains all pending events
- **WHEN** a caller runs a simulation with pending events until completion
- **THEN** all pending events are executed in deterministic `(time, sequence)` order and the queue becomes empty

#### Scenario: Run on empty queue returns zero-step result
- **WHEN** a caller runs a simulation with no pending events until completion
- **THEN** the library returns a result with zero steps executed and the current state unchanged

#### Scenario: Run until time executes only eligible events
- **WHEN** a caller runs a simulation until a target time
- **THEN** only events with execution times less than or equal to that target time are executed, and any remaining events have times strictly greater than the target

#### Scenario: Reject run-until-time in the past
- **WHEN** a caller requests a time-bounded run with a target time earlier than the current simulation time
- **THEN** the library throws `IllegalArgumentException`

#### Scenario: Run until predicate evaluates before each step
- **WHEN** a caller runs a simulation with a stopping predicate
- **THEN** the predicate is evaluated before each step, and execution stops without executing that step once the predicate returns true

#### Scenario: Run until predicate satisfied immediately
- **WHEN** a caller runs a simulation with a predicate that is already satisfied
- **THEN** zero steps are executed and the current state is returned unchanged

#### Scenario: Reject null predicate
- **WHEN** a caller requests a predicate-bounded run with a null predicate
- **THEN** the library throws `NullPointerException`

### Requirement: Simulation behavior is deterministic for equivalent inputs
For the same initial state and the same sequence of scheduling operations, the library MUST produce the same event execution order, time progression, and final state on every run. This property follows from the strict `(time, sequence)` total order and pure functional action model, but MUST hold as an independent guarantee.

#### Scenario: Replaying the same schedule yields the same result
- **WHEN** two simulations are created with the same initial state and receive the same scheduling operations in the same order
- **THEN** they produce the same sequence of executed events, the same time progression, and the same final state

#### Scenario: Determinism holds when actions produce new events
- **WHEN** two simulations are created with the same initial state and the same actions that produce new events via `ActionResult`
- **THEN** both simulations produce the same execution trace including all dynamically produced events

### Requirement: Functional-style usage expectations are documented
The library SHALL document that the simulation engine is intended for single-threaded use and that event actions MUST be pure functions of type `S -> ActionResult<S>`. The documentation MUST explain that actions express scheduling intent declaratively through `ActionResult` rather than by calling engine methods directly. The documentation MUST explain that mutable state or side effects in actions may break functional semantics even if the engine still executes events deterministically.

#### Scenario: API documentation describes the pure action model
- **WHEN** a caller reads the public API documentation
- **THEN** the documentation explains that actions return `ActionResult<S>` to express both state changes and new event scheduling, and that actions must not interact with the simulation engine directly

#### Scenario: API documentation describes user obligations
- **WHEN** a caller reads the public API documentation
- **THEN** the documentation explains single-threaded usage, immutable state expectations, and the effect of impure actions on functional semantics

### Requirement: Critical types and operations include JML specifications
The library SHALL include JML annotations on critical record types and core simulation operations. JML specifications MUST cover at minimum: key invariants on the `Simulation` class (such as monotonic time and non-negative sequence counters), preconditions and postconditions on `step()`, and preconditions on at least one scheduling method. The JML specifications do not need to be complete or exhaustive but MUST be syntactically valid and checkable by OpenJML.

#### Scenario: Simulation class has JML class-level invariants
- **WHEN** OpenJML processes the `Simulation` class
- **THEN** JML invariant annotations are present that express at minimum that the current time is non-negative and the sequence counter is non-negative

#### Scenario: Step operation has JML pre and postconditions
- **WHEN** OpenJML processes the `step()` method
- **THEN** JML `requires` and `ensures` clauses are present that express at minimum the non-empty queue precondition and the time-advancement postcondition

#### Scenario: At least one scheduling method has JML preconditions
- **WHEN** OpenJML processes `scheduleAt()` or `scheduleIn()`
- **THEN** JML `requires` clauses are present that express at minimum the time or delay validity precondition

#### Scenario: JML annotations are syntactically valid
- **WHEN** OpenJML runs extended static checking on annotated methods
- **THEN** the annotations parse without syntax errors
