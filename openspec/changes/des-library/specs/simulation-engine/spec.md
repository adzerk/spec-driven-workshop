## ADDED Requirements

### Requirement: Initialize simulation state
The simulation engine SHALL provide an initialization operation that creates a valid empty simulation state from a non-negative current time and a non-null payload.

#### Scenario: Initialize an empty state
- **WHEN** a caller initializes the engine with time `0` and a non-null payload
- **THEN** the returned state has current time `0`, zero processed events, zero pending events, and no queued work

#### Scenario: Reject negative initial time
- **WHEN** a caller initializes the engine with a negative time
- **THEN** the engine rejects the request with `IllegalArgumentException`

#### Scenario: Reject null initial payload
- **WHEN** a caller initializes the engine with a null payload
- **THEN** the engine rejects the request with `NullPointerException`

### Requirement: Schedule future or current events
The simulation engine SHALL allow callers to schedule events at the current simulation time or later without mutating the existing payload.

#### Scenario: Schedule an event at the current time
- **WHEN** a caller schedules an event whose scheduled time equals the state's current time
- **THEN** the returned state contains that event as pending and preserves the existing payload

#### Scenario: Schedule multiple events in caller order
- **WHEN** a caller schedules multiple valid events in one operation
- **THEN** the returned state contains all of them as pending work and preserves their relative insertion order for deterministic tie-breaking

#### Scenario: Reject past scheduling
- **WHEN** a caller schedules an event earlier than the state's current time
- **THEN** the engine rejects the request with `IllegalArgumentException`

### Requirement: Expose deterministic next-event inspection
The simulation engine SHALL allow callers to inspect the next event to execute without consuming it, and that event SHALL be the minimum pending event under the engine's deterministic ordering rules.

#### Scenario: Inspect an empty queue
- **WHEN** a caller inspects the next event of a state with no pending events
- **THEN** the engine returns an empty result and leaves the state unchanged

#### Scenario: Inspect the earliest pending event
- **WHEN** a caller inspects a state containing multiple pending events
- **THEN** the engine returns the event that would execute next under deterministic ordering and does not consume it

### Requirement: Execute one event at a time
The simulation engine SHALL execute exactly one next event per step, advance current time to that event's scheduled time, update payload from the action result, and merge any emitted valid events into pending work.

#### Scenario: Step a non-empty state
- **WHEN** a caller steps a state whose next event is scheduled for time `5`
- **THEN** the returned state has current time `5`, processed-event count increased by one, and one fewer original pending event before emitted events are merged

#### Scenario: Execute simultaneous events deterministically
- **WHEN** two pending events share the same scheduled time
- **THEN** repeated executions from identical starting states consume them in the same deterministic order

#### Scenario: Reject stepping an empty state
- **WHEN** a caller steps a state with no pending events
- **THEN** the engine rejects the request with `IllegalStateException`

### Requirement: Validate action results during stepping
The simulation engine SHALL validate the result returned by an event action during `step` and fail deterministically if the result violates engine contracts.

#### Scenario: Reject null transition result
- **WHEN** an event action returns null instead of a transition result
- **THEN** the engine fails the step with `IllegalStateException`

#### Scenario: Reject malformed emitted event
- **WHEN** an event action emits an event scheduled before the current step time or with an invalid action
- **THEN** the engine fails the step with `IllegalStateException`

### Requirement: Support bounded step execution
The simulation engine SHALL provide an operation that executes at most a caller-specified number of events and preserves all engine invariants.

#### Scenario: Run zero steps
- **WHEN** a caller requests zero steps
- **THEN** the engine returns an equivalent state without consuming any events

#### Scenario: Stop early when queue empties
- **WHEN** a caller requests more steps than there are pending events
- **THEN** the engine executes only the available events and stops without error

#### Scenario: Reject negative step limit
- **WHEN** a caller requests a negative maximum step count
- **THEN** the engine rejects the request with `IllegalArgumentException`

### Requirement: Support bounded time execution
The simulation engine SHALL provide an operation that executes all events scheduled at or before a caller-specified limit time, including newly emitted events that also fall within that bound.

#### Scenario: Execute events on the boundary
- **WHEN** a caller runs the engine until time `10` and a pending or newly emitted event is scheduled exactly at `10`
- **THEN** the engine executes that event before returning

#### Scenario: Preserve events after the boundary
- **WHEN** a caller runs the engine until time `10` and pending events remain after execution
- **THEN** every remaining pending event is scheduled strictly after `10`

#### Scenario: Reject a past time limit
- **WHEN** a caller runs the engine until a limit earlier than the state's current time
- **THEN** the engine rejects the request with `IllegalArgumentException`

### Requirement: Preserve simulation invariants
The simulation engine SHALL preserve a valid simulation state after every successful operation.

#### Scenario: Maintain monotonic counters and time
- **WHEN** a caller performs any successful sequence of initialize, schedule, inspect, step, bounded step, or bounded time operations
- **THEN** current time, processed-event count, and internal ordering metadata never decrease

#### Scenario: Maintain valid pending events
- **WHEN** a caller performs any successful engine operation
- **THEN** every pending event remains non-null, scheduled at or after current time, and ordered consistently with the engine's deterministic ordering rule
