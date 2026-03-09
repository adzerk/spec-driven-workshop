## ADDED Requirements

### Requirement: Simulation creation establishes an initial deterministic state
The library SHALL provide a way to create a simulation with a non-null initial state. A newly created simulation MUST start at time `0`, MUST contain no pending events, and MUST expose the provided initial state as its current state.

#### Scenario: Create simulation with valid initial state
- **WHEN** a caller creates a simulation with a non-null initial state
- **THEN** the simulation starts at time `0`, has an empty event queue, and reports the provided value as the current state

#### Scenario: Reject null initial state
- **WHEN** a caller creates a simulation with a null initial state
- **THEN** the library rejects the call with a null-related failure

### Requirement: Scheduling preserves future-only event ordering
The library SHALL allow callers to schedule events at an absolute time or relative delay. Scheduled events MUST have execution times greater than or equal to the simulation's current time, and events scheduled for the same time MUST execute in the order they were scheduled.

#### Scenario: Schedule an event at a future absolute time
- **WHEN** a caller schedules an event at a time greater than or equal to the current simulation time
- **THEN** the event is added to the pending queue for that exact time

#### Scenario: Schedule an event by relative delay
- **WHEN** a caller schedules an event with a non-negative delay
- **THEN** the event is added at the current simulation time plus that delay

#### Scenario: Reject scheduling in the past
- **WHEN** a caller attempts to schedule an event at a time earlier than the current simulation time
- **THEN** the library rejects the call with an argument-related failure

#### Scenario: Preserve FIFO ordering for equal-time events
- **WHEN** multiple events are scheduled for the same execution time
- **THEN** those events execute in the order they were originally scheduled

#### Scenario: Reject negative relative delay
- **WHEN** a caller schedules an event with a negative delay
- **THEN** the library rejects the call with an argument-related failure

#### Scenario: Reject overflow when computing delayed time
- **WHEN** a caller schedules an event with a delay that overflows the simulation time domain
- **THEN** the library rejects the call with an arithmetic-related failure

### Requirement: Stepping executes exactly one next event
The library SHALL provide a step operation that executes exactly one pending event, chosen by the minimum `(time, order)` pair. Executing a step MUST advance simulation time to that event's time, apply the event action to the previous state, remove that event from the queue, and preserve all simulation invariants.

#### Scenario: Step executes the earliest scheduled event
- **WHEN** a caller steps a simulation that has pending events
- **THEN** the earliest event by time and scheduling order is executed and removed from the queue

#### Scenario: Step updates time and state
- **WHEN** a caller steps a simulation with a pending event
- **THEN** the simulation time becomes the event time and the simulation state becomes the result of applying that event's action

#### Scenario: Reject step on empty queue
- **WHEN** a caller steps a simulation with no pending events
- **THEN** the library rejects the call with a state-related failure

### Requirement: Run operations stop at defined boundaries
The library SHALL provide run operations for draining all pending events, stopping at a time boundary, and stopping when a predicate indicates completion. Each run variant MUST execute zero or more valid steps and MUST stop only at the boundary defined by that variant.

#### Scenario: Run drains all pending events
- **WHEN** a caller runs a simulation with pending events until completion
- **THEN** all pending events are executed in deterministic order and the queue becomes empty

#### Scenario: Run until time executes only eligible events
- **WHEN** a caller runs a simulation until a target time
- **THEN** only events with execution times less than or equal to that target time are executed

#### Scenario: Reject run-until-time in the past
- **WHEN** a caller requests a time-bounded run with a target time earlier than the current simulation time
- **THEN** the library rejects the call with an argument-related failure

#### Scenario: Run until predicate stops once condition is satisfied
- **WHEN** a caller runs a simulation with a stopping predicate
- **THEN** execution stops after zero or more steps once the predicate is satisfied according to the library's documented evaluation semantics

### Requirement: Simulation behavior is deterministic for equivalent inputs
For the same initial state and the same sequence of scheduling operations, the library MUST produce the same event execution order, time progression, and final state on every run.

#### Scenario: Replaying the same schedule yields the same result
- **WHEN** two simulations are created with the same initial state and receive the same scheduling operations in the same order
- **THEN** they produce the same sequence of executed events, the same time progression, and the same final state

### Requirement: Functional-style usage expectations are documented
The library SHALL document that the simulation engine is intended for single-threaded use and that event actions are expected to behave like pure state transitions. The documentation MUST explain that mutable state or side effects may break functional semantics even if the engine still executes events deterministically.

#### Scenario: API documentation describes user obligations
- **WHEN** a caller reads the public API documentation
- **THEN** the documentation explains single-threaded usage, state expectations, and the effect of impure event actions on functional semantics
