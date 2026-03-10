# Analyze and optimize code for performance (@plan subagent)

---

## User

# Code Optimization Analysis

You are a code optimization specialist focused on performance.
When provided with @src/main/java/com/kevel/des/  (file paths or directories), analyze and optimize the specified code. If no arguments provided, analyze the current context (open files, recent changes, or project focus).

## Your Optimization Process:

**Step 1: Determine Analysis Scope**
- If @src/main/java/com/kevel/des/  provided: Focus on specified files/directories
- If no arguments: Analyze current context by checking:
  - Recent changes used in this context
  - Recently modified files via `git status` and `git diff --name-only HEAD~5`
- Identify file types and applicable optimization strategies

**Step 2: Performance Analysis**
Execute comprehensive performance review:

1. **Algorithmic Efficiency**
   - Identify O(n log n) or worse time complexity patterns
   - Identify any space complexity worse than O(n)
   - Look for unnecessary nested loops
   - Find redundant calculations or database queries
   - Spot inefficient data structure usage
   - Suggest better algorithic approaches where possible
   - Suggest better data structure options where possible

2. **Memory Management**
   - Detect memory leaks and excessive allocations
   - Find large objects that could be optimized
   - Identify unnecessary data retention
   - Identify reading contents of files or payloads completely into memory
   - Check for proper cleanup in event handlers

3. **I/O Optimization**
   - Analyze file read/write patterns
   - Check for unnecessary API calls
   - Look for missing caching opportunities
   - Identify blocking operations that could be async
   - Database: N+1 queries, missing indexes

**Step 3: Present Optimization Report**

## Code Optimization Analysis

### Analysis Scope
- **Files Analyzed**: [List of files examined]
- **Total Lines**: [Code volume analyzed]
- **Languages**: [Programming languages found]
- **Frameworks**: [Frameworks/libraries detected]

### Performance Issues Found

#### 🔴 Critical Performance Issues
- **Issue**: [Specific performance problem]
- **Location**: [File:line reference]
- **Impact**: [Performance cost/bottleneck]
- **Solution**: [Specific optimization approach]

#### 🟡 Performance Improvements
- **Optimization**: [Improvement opportunity]
- **Expected Gain**: [Performance benefit]
- **Implementation**: [How to apply the fix]

### Optimization Recommendations

#### Priority 1 (Critical)
1. [Most important optimization with immediate impact]
2. [Critical security fix needed]
3. [Performance bottleneck to address]

#### Priority 2 (Important)
1. [Significant improvements to implement]
2. [Important edge cases to handle]

#### Priority 3 (Nice to Have)
1. [Code quality improvements]
2. [Minor optimizations]

### Implementation Guide
```
[Specific code examples showing how to implement key optimizations]
```

### Expected Impact
- **Performance**: [Expected speed/efficiency gains]

## Optimization Focus Areas:
- **Performance First**: Identify and fix actual bottlenecks, not premature optimizations
- **Measurable Improvements**: Focus on changes that provide tangible benefits

---

## Assistant (Plan · claude-opus-4.6 · 5.9s)



I'll analyze the code in `src/main/java/com/kevel/des/` for performance optimization opportunities. Let me read all the files first.

---

## Assistant (Plan · claude-opus-4.6 · 56.1s)

Now I have a complete picture of the codebase. Here is my analysis:

---

## Code Optimization Analysis

### Analysis Scope
- **Files Analyzed**: 6 source files + 2 test files
- **Total Lines**: ~475 (source), ~368 (tests)
- **Language**: Java (records, generics, functional interfaces)
- **Framework**: Standalone discrete-event simulation (DES) engine; no external runtime dependencies

### Performance Issues Found

#### 🔴 Critical Performance Issues

**1. Redundant defensive iteration in `ActionResult` constructor**
- **Location**: `ActionResult.java:41`
- **Issue**: `scheduledEvents.forEach(...)` iterates the entire list for null-checking, then `List.copyOf()` on line 42 iterates it *again* (and also performs its own internal null check on each element). This is two full traversals where one suffices.
- **Impact**: For actions that schedule many follow-on events, this doubles the iteration cost on every action result construction. In a hot simulation loop, this is on the critical path.
- **Solution**: Remove the explicit `forEach` null-check and rely on `List.copyOf()` which already throws `NullPointerException` on null elements. If a custom error message is important, replace with a single-pass manual copy.

**2. `step()` calls `scheduleInternal()` per produced event -- no batching**
- **Location**: `Simulation.java:171-176`
- **Issue**: Each `scheduleInternal` call does `queue.add()` which is O(log n) for a `PriorityQueue`. When an action produces `k` follow-on events, this is O(k log n). If events were added to an unsorted buffer and then heapified, it could be O(k + n) amortized for bulk inserts via `PriorityQueue.addAll()` (though Java's `PriorityQueue.addAll` is still individual inserts). More significantly, each call also does two `Math.addExact` calls with overflow checking.
- **Impact**: Moderate. Only matters when actions frequently produce multiple follow-on events.
- **Solution**: This is a minor concern for the current scale. If bulk scheduling becomes common, consider a bulk-add path or switching to a different heap implementation (e.g., a pairing heap) that supports efficient merge.

#### 🟡 Performance Improvements

**3. `EventId` allocation on every event**
- **Location**: `Simulation.java:265`
- **Issue**: A new `EventId` record is allocated for every scheduled event. While records are lightweight, in a tight simulation loop with millions of events, this creates GC pressure. `EventId` is a simple wrapper around a `long`.
- **Expected Gain**: Reduced GC pressure for high-throughput simulations.
- **Implementation**: If `EventId` is primarily used for identity comparison and logging, consider returning the raw `long` from `scheduleInternal` and wrapping lazily, or using a value-type approach. However, the record provides type safety, so this is a trade-off. On modern JVMs with escape analysis, the JIT may already eliminate the allocation in many cases.

**4. `Event` record allocation per event**
- **Location**: `Simulation.java:271`
- **Issue**: Each event creates an `Event` record wrapping `(time, sequence, eventId, action)`. Combined with `EventId`, that's two object allocations per scheduled event.
- **Expected Gain**: Minor. JVM escape analysis may help, but heap-stored objects (added to `PriorityQueue`) cannot be escape-analyzed away.
- **Implementation**: Consider inlining `EventId` as a raw `long` field in `Event` to eliminate one allocation per event. This would require `StepResult` to also use raw `long` for the event ID.

**5. `Math.addExact` overflow checks on every scheduling call**
- **Location**: `Simulation.java:266, 269`
- **Issue**: `Math.addExact` performs overflow checking on every event scheduling. For sequence counters and event IDs that will realistically never reach `Long.MAX_VALUE`, this adds a branch on the hot path.
- **Expected Gain**: Trivial per call, but measurable at very high event throughput.
- **Implementation**: Replace with simple `++` increment if overflow is not a realistic concern. Even at 1 billion events/second, it would take ~292 years to overflow a `long`.

**6. `List.copyOf()` in `ActionResult` for common zero-event case**
- **Location**: `ActionResult.java:42`
- **Issue**: `ActionResult.of(newState)` already passes `List.of()` (an immutable empty list), but the compact constructor still calls `List.copyOf()` on it. `List.copyOf` does check if the input is already an unmodifiable list and short-circuits, so this is actually already optimized by the JDK. No change needed.
- **Expected Gain**: None -- JDK already handles this.

**7. `runUntilTime` peeks twice per iteration**
- **Location**: `Simulation.java:219-224`
- **Issue**: `queue.peek()` is called once before the loop and once at the end of each iteration. Meanwhile, `step()` internally calls `queue.poll()`. The second `peek()` after `step()` is necessary since `step()` may have added new events. This is correct and `peek()` is O(1), so no issue here.
- **Expected Gain**: None -- this is already correct.

**8. `runUntil` evaluates predicate on state before first step**
- **Location**: `Simulation.java:248-249`
- **Issue**: The predicate is called on `currentState` before checking if the queue is empty. If the predicate is expensive and the queue is empty, this is wasted work. However, this is semantically correct (the user wants to know if the condition is met regardless of queue state).
- **Expected Gain**: Negligible in practice.

### Optimization Recommendations

#### Priority 1 (Critical)

1. **Remove redundant null-check iteration in `ActionResult` constructor** -- The `forEach` null-check loop at line 41 is fully redundant with `List.copyOf()` at line 42, which already rejects null elements. Removing it eliminates one full list traversal on every action result construction, which is on the simulation hot path.

#### Priority 2 (Important)

2. **Consider inlining `EventId` as a raw `long`** -- Eliminating the `EventId` wrapper allocation saves one object per event. The trade-off is reduced type safety. A middle ground: keep `EventId` as public API but use raw `long` internally in `Event` and `Simulation`, wrapping only when returned to the caller via `scheduleAt`/`scheduleIn`.

3. **Replace `Math.addExact` with plain increment for sequence/eventId counters** -- Overflow of a `long` counter is unreachable in practice. The overflow check adds an unnecessary branch on the hot scheduling path.

#### Priority 3 (Nice to Have)

4. **Consider a more allocation-efficient heap** -- Java's `PriorityQueue` stores `Object[]` and requires boxing/wrapping. For very high-throughput simulations (millions of events), a custom long-keyed min-heap storing primitive `(time, sequence)` keys with object references for actions could reduce memory overhead and improve cache locality.

5. **Pool or flyweight `StepResult` objects** -- `step()` allocates a `StepResult` on every call. In `run()`, `runUntilTime()`, and `runUntil()`, the result is immediately discarded. Consider adding internal step methods that don't allocate `StepResult` when the caller doesn't need it.

### Implementation Guide

**Priority 1 fix** -- Remove redundant null check in `ActionResult`:
```java
// Before (two passes):
public ActionResult {
    Objects.requireNonNull(newState, "newState must not be null");
    Objects.requireNonNull(scheduledEvents, "scheduledEvents must not be null");
    scheduledEvents.forEach(event -> Objects.requireNonNull(event, "scheduled event must not be null"));
    scheduledEvents = List.copyOf(scheduledEvents);
}

// After (one pass):
public ActionResult {
    Objects.requireNonNull(newState, "newState must not be null");
    Objects.requireNonNull(scheduledEvents, "scheduledEvents must not be null");
    scheduledEvents = List.copyOf(scheduledEvents);  // already rejects null elements
}
```

**Priority 2 fix** -- Replace overflow-checked increment:
```java
// Before:
nextEventId = Math.addExact(nextEventId, 1L);
nextSequence = Math.addExact(nextSequence, 1L);

// After:
nextEventId++;
nextSequence++;
```

**Priority 2 fix** -- Avoid `StepResult` allocation in run loops:
```java
// Add a private method that steps without allocating StepResult:
private void stepInternal() {
    Event<S> nextEvent = Objects.requireNonNull(queue.poll());
    currentTime = nextEvent.time();
    ActionResult<S> actionResult = nextEvent.action().apply(currentState);
    currentState = actionResult.newState();
    for (ActionResult.ScheduledEvent<S> descriptor : actionResult.scheduledEvents()) {
        if (descriptor.time() < currentTime) {
            throw new IllegalArgumentException("action-produced event is in the past");
        }
        scheduleInternal(descriptor.time(), descriptor.action());
    }
}

// Use in run():
public RunResult<S> run() {
    long startTime = currentTime;
    long steps = 0L;
    while (!queue.isEmpty()) {
        stepInternal();
        steps++;
    }
    return new RunResult<>(steps, startTime, currentTime, currentState, false);
}
```

### Expected Impact

- **Priority 1**: Eliminates ~50% of iteration cost in `ActionResult` construction. Measurable in simulations where actions frequently schedule follow-on events.
- **Priority 2 (overflow checks)**: Removes 2 conditional branches per event scheduling. Micro-optimization, measurable only at very high throughput.
- **Priority 2 (StepResult)**: Eliminates 1 object allocation per step in `run()`/`runUntilTime()`/`runUntil()`. For a simulation with N steps, saves N allocations and reduces GC pressure.
- **Priority 3**: Structural changes for extreme-scale simulations. Only worth pursuing if benchmarking shows the heap or allocation as a bottleneck.

### Summary

This is a well-structured, clean DES engine. The code is already quite efficient -- it uses `PriorityQueue` (O(log n) heap), immutable records, and deterministic ordering. The issues found are micro-optimizations rather than algorithmic problems. The most impactful change is **removing the redundant null-check loop in `ActionResult`** and **avoiding `StepResult` allocation in the run loop methods**, both of which are on the hot path of every simulation step.

---

