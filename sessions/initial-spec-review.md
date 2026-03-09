# Explore mode: guidelines, scope, and guardrails

**Session ID:** ses_32d1bc89dffetmln858hE3WS8y
**Created:** 3/9/2026, 9:58:17 AM
**Updated:** 3/9/2026, 4:25:59 PM

---

## Assistant (Plan · claude-opus-4.6 · 67.2s)

Now I have everything I need for a thorough review. Here's my analysis:

---

## Artifact Review: `des-library`

### Overall Assessment

The artifacts are well-structured and internally consistent. The proposal motivates the change clearly, the design makes defensible decisions, the spec covers the core behavioral requirements, and the tasks follow a logical dependency order. That said, I found several issues worth addressing, ranging from minor gaps to a few things that could cause real confusion during implementation.

---

### 1. Inconsistencies

**1a. Design migration plan has a duplicate step number** (`design.md:83-84`)
Steps 3 and 4 are both numbered "4":
```
3. Add contract-oriented Javadoc...
4. Ensure the code is defensive...   ← should be 4
4. Add unit and jqwik tests.         ← should be 5
5. Add a minimal example...          ← should be 6
```

**1b. Spec says `(time, order)` but design says `(time, sequence)`** (`spec.md:42` vs `design.md:50`)
The stepping requirement says "minimum `(time, order)` pair" but everywhere else -- design, prior exploration, and other spec sections -- it's called `sequence`. This inconsistency could confuse an implementer about whether `order` and `sequence` are the same concept.

**1c. `Event<S>` is in the design's public type list but not specified in the spec** (`design.md:29`)
The design mentions `Simulation<S>`, `EventId`, `StepResult<S>`, and `RunResult<S>` as public types. The prior exploration also includes `Event<S>` as a public record exposed by `peekNext()`. The spec references events conceptually but never specifies what an event record looks like or what `peekNext()` returns. This is a gap -- the implementer needs to know whether `Event<S>` is part of the public API.

---

### 2. Missing Specifications

**2a. No spec for `peekNext()`**
The prior exploration (`session-initial-explore_1.md:85`) defines `peekNext()` with clear contracts: returns `Optional<Event<S>>`, empty iff queue empty, does not modify simulation. The spec has no requirement covering observation of the next event. The prior test suite even has `peekNext_isNonMutating`.

**2b. No spec for `EventId` return semantics from scheduling**
The prior exploration specifies that `scheduleAt` and `scheduleIn` return a non-null `EventId`, and that sequence numbers are monotonically increasing. The spec says an event "is added to the pending queue" but never specifies what the caller gets back. If `EventId` matters (and the design lists it as a public type), this needs a requirement.

**2c. No spec for scheduling *during* event execution (self-scheduling)**
A very common DES pattern is an event action that schedules new events. For example:
```java
sim.scheduleAt(10, s -> {
    sim.scheduleAt(20, s2 -> ...);  // new event from within action
    return newState;
});
```
The spec and design are silent on whether this is allowed, what happens to the simulation's `now` during action execution, and whether re-entrant scheduling is valid. The prior exploration's functional-core guidance suggests actions should be pure `S -> S`, which would preclude this pattern -- but that's a convention, not a spec. This is probably the single biggest behavioral ambiguity.

**2d. No spec for `null` action in `scheduleAt`/`scheduleIn`**
The spec covers past-time rejection and overflow but doesn't specify what happens when `action` is `null`. The prior exploration and the existing test suite (`scheduleAt_andScheduleIn_rejectNullAction`) both expect `NullPointerException`. The design mentions `Objects.requireNonNull` in the prior exploration but neither the current spec nor design explicitly states this.

**2e. No spec for `run()` on an already-empty queue**
The spec says "run drains all pending events" but doesn't address the edge case of running when the queue is already empty. Should it return a zero-step result? Throw? The prior exploration implies zero steps is valid (postcondition: "executes zero or more steps"), but the scenario text says "with pending events."

**2f. No spec for `runUntil` with a null predicate**
The existing test suite has `runUntil_rejectsNullCondition`. Neither the spec nor design addresses null predicate validation.

---

### 3. Logical Concerns

**3a. Predicate evaluation semantics are left vague** (`spec.md:72-73`)
The scenario says "execution stops... once the predicate is satisfied according to the library's documented evaluation semantics." This is circular -- it says "follow the docs" but the docs are what we're writing. The key question: is the predicate checked *before* each step, *after* each step, or both? This determines whether zero steps is possible and whether the stop happens before or after the triggering state change. The prior exploration's run loop checks `!stopCondition.test(this)` as a loop condition (so: checked before each step), but this needs to be explicit.

**3b. Determinism requirement is very strong but hard to test precisely** (`spec.md:75-80`)
The requirement says "the same event execution order, time progression, and final state on every run." This is correct but the single scenario ("replaying the same schedule yields the same result") only tests two runs. Determinism is really a property of the implementation's ordering guarantees, which are already covered by the FIFO and `(time, sequence)` requirements. Consider whether this requirement adds independent value or is redundant with the ordering specs.

**3c. "Functional-style usage expectations are documented" is a documentation requirement, not a behavioral requirement** (`spec.md:82-87`)
Its scenario ("API documentation describes user obligations") is not testable in the same way as the other scenarios. That's fine -- it's valid as a spec -- but it's a different *kind* of requirement and the tasks should handle it differently (review Javadoc, not write a test).

---

### 4. Gaps Between Artifacts

**4a. Prior exploration is much richer than the current spec**
The exploration session (`session-initial-explore_1.md`) includes detailed contracts for every method, specific exception types, queue-size postconditions, and a full model-based testing strategy. The spec is significantly more abstract. This is defensible (specs should be implementation-neutral) but the implementer will need to reference both documents. Consider whether the spec should be the authoritative behavioral contract or whether the exploration's detail should be folded in.

**4b. Prior test suite had 17 unit tests + 5 property tests; tasks don't estimate scope**
The tasks list testing as three items (4.1, 4.2, 4.3) but the prior implementation required 22 tests across two classes. The tasks might benefit from being more granular here, since testing is roughly half the implementation effort.

**4c. Design open questions are unresolved**
The design lists three open questions (`design.md:91-93`) and provides "current direction" answers. These are decisions that should be resolved before implementation starts, because they affect the public API shape:
- Mutable internals vs. persistent API
- `peekNext()` return type
- JML annotations

The spec and tasks don't reflect these as resolved decisions. An implementer could go either way.

---

### 5. Suggested Improvements

1. **Add a `peekNext()` requirement to the spec** with scenarios for non-empty, empty, and non-mutation.
2. **Add an `EventId` return requirement** to the scheduling requirement or as a standalone requirement.
3. **Resolve the self-scheduling question** explicitly: either spec it as allowed (with constraints) or spec it as undefined/prohibited behavior.
4. **Add null-action rejection scenarios** to the scheduling requirement.
5. **Normalize terminology**: pick either `sequence` or `order` and use it consistently.
6. **Specify predicate evaluation timing** in the run-until-predicate requirement.
7. **Add edge-case scenarios**: `run()` on empty queue, `runUntil(null)`, `scheduleAt(now, ...)` (at current time, not just "future").
8. **Resolve the design's open questions** and update the design to reflect final decisions.
9. **Consider splitting tasks 4.1-4.3 into finer-grained items** given the prior implementation's test count.
10. **Fix the duplicate step numbering** in `design.md`'s migration plan.

---

### Clarifying Questions for You

1. **Self-scheduling**: Should event actions be allowed to call `scheduleAt`/`scheduleIn` on the simulation during their execution? This is architecturally significant -- it determines whether `Simulation` needs to handle re-entrant calls or whether actions are truly pure `S -> S`.

2. **`peekNext()` visibility**: The design's open question asks whether to expose the full `Event<S>` record. Do you want `peekNext()` in the v1 public API at all? If yes, should it expose the sequence number (which is an internal ordering detail)?

3. **JML annotations**: The design's current direction says "no JML in v1." Given that the workshop's whole premise is spec-driven development with JML, is that the right call? Even a few key annotations (e.g., on `step()` and `scheduleAt()`) would demonstrate the technique scaling to a richer domain.

4. **Test granularity**: The tasks currently have 3 testing items. The prior implementation had 22 tests. Do you want the tasks to enumerate specific test cases, or is the current grouping sufficient for guiding implementation?

5. **Spec abstraction level**: The current spec uses phrases like "argument-related failure" and "state-related failure" rather than naming specific exception types. This is more implementation-neutral but less precise. Do you prefer this level of abstraction, or should the spec name the exact exception types (`IllegalArgumentException`, `IllegalStateException`, `NullPointerException`, `ArithmeticException`) as the prior exploration does?

---

