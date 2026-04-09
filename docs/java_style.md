# Java Style Guide

A style guide for reliable, performant, evolvable Java systems.

This guide takes direct inspiration from [NASA's Power of 10](https://en.wikipedia.org/wiki/The_Power_of_10:_Rules_for_Developing_Safety-Critical_Code), [TigerStyle](https://tigerstyle.dev/), with additional influence from [Datadog](https://github.com/nerdsane/redis-rust/blob/main/docs/RUST_STYLE.md), Firecracker, DataFusion, and FoundationDB.

The priorities are:

1. Safety
2. Performance
3. Developer experience

Style is not decoration. Style is design pressure applied early enough to prevent defects, simplify reasoning, and lower maintenance cost.

## Quick Reference

| Pattern | Rule |
|---|---|
| Design | Specify invariants, legal transitions, failures, and bounds before coding |
| System shape | Prefer deterministic systems and explicit state machines |
| Control flow | Keep control flow simple; bound loops; keep a method within 70 lines |
| Bounds | Put a bound on queues, retries, buffers, input sizes, and in-flight work |
| Error handling | Use `Result` for expected failures; exceptions only for exceptional failures |
| Types | Prefer sealed types, records, and `Box<Tag, T>` over flags and raw primitives |
| Assertions | Assert preconditions, postconditions, invariants, and bounds |
| Arithmetic | Use exact arithmetic and explicit units; never assume no overflow |
| Verification | Design for simulation, differential testing, and fault injection |
| Concurrency | Prefer deterministic concurrency, partitioned ownership, shared-nothing/message passing, and explicit ordering |
| Performance | Target zero allocation in hot paths; avoid copies; prefer cache-friendly layouts |
| Documentation | All classes and methods need Javadoc with preconditions, postconditions, invariants, exceptions, and safety requirements |
| Tooling | Zero compiler warnings; zero normalized analyzer debt |

## The Rules

### 1. Design around invariants first.

Before writing production code, state:

- what must always be true;
- what must never happen;
- what states exist;
- what transitions are legal;
- what failures are expected;
- what limits must hold.

For non-trivial work, start with a small design sketch, state table, or executable reference model. Prefer a lightweight model that engineers can maintain over a grand formal artifact that nobody updates.

Reason: most expensive defects are introduced in requirements and design. [Invariants](https://brooker.co.za/blog/2023/07/28/ds-testing.html) are the strongest bridge between design, implementation, testing, and verification.

### 2. Build deterministic systems, ideally as explicit state machines.

Model lifecycle and protocol behavior with sealed interfaces, records, enums, or typestate.  Favor pure functions and methods.  Prefer value-oriented programming.

- Illegal transitions should be impossible or difficult to express.
- State changes should be explicit and local.
- Side effects and nondeterminism should live at the edges. (I/O, time, dynamic/conditional/flag handling behavior)
- Core transitions should be deterministic.

Prefer designs that can be explained as "given state S and input I, produce state S' and output O".

Reason: deterministic [state machines](state_machines.md) are easier to reason about, test, replay, verify, and evolve. They also expose invariants clearly.

### 3. Use only simple, explicit control flow.

- Prefer early returns over deep nesting.
- Avoid recursion in production paths unless the bound is trivial and documented.
- Avoid clever control flow and hidden callbacks in core logic.
- Split compound boolean logic when correctness matters.
- Every loop must have a visible upper bound, or the non-termination must be explicit and justified.

Hard target: keep methods within 70 lines.

Push `if`s up and loops down. Let parent methods own control flow; let helpers do pure or near-pure work.

Reason: simple control flow is easier for reviewers, static analyzers, and formal tools to understand.

### 4. Put a bound on everything.

Bound loops, queues, retries, batch sizes, buffer sizes, input sizes, timeouts, and memory growth.

- Unbounded work is a bug unless proven otherwise.
- Use named constants for operational limits.
- Make bounds visible at the call site.
- Fail fast when limits are exceeded.

If a loop is intentionally unbounded, assert the surrounding invariant that makes it safe, such as "event loop processes one bounded batch per tick".

Reason: real systems have limits. Explicit bounds prevent infinite loops, latency spikes, memory blowups, and hand-wavy designs.

### 5. Use `Result` for expected failures; use exceptions for exceptional failures.

For domain, validation, parsing, lookup, and state-transition failures, return `Result<T, E>`.

- Model `E` with an `enum` or sealed error hierarchy. `E` can also be an Exception to bridge between Results and Exceptions.
- Handle `Result` explicitly with `switch`, `map`, `flatMap`, or `fold`.
- Do not ignore `Result` values.
- Do not call `get()` on an `Err` in production logic.

Use exceptions only for:

- broken invariants;
- impossible states;
- programmer errors;
- infrastructure failures that are truly exceptional at the current layer.

Use checked exceptions for exceptional situations outside the control of the program.

Reason: foreseeable production failures are part of ordinary behavior and should be represented as data. This keeps control flow explicit and makes error handling testable. See `src/main/java/com/kevel/util/Result.java`.

### 6. Encode semantics in types.

Use Java 21's type system to make invalid states harder to represent.

- Use sealed interfaces and records for algebraic domains.
- Use `Result<T, E>` for success/failure.
- Use `Optional<T>` for presence/absence only.  Avoid using Optional when performance matters most.
- Use `Box<Tag, T>` when the runtime representation is simple but the logical type matters.
- Use tagged values or witnesses for capabilities and trust boundaries -- `Box<Tag, T>` provides this.

Avoid boolean flags when the state deserves a type. Avoid reusing raw `String`, `long`, or `Map` values when a semantic wrapper would prevent confusion.

Reason: types are the cheapest always-on verifier we have. `Box` demonstrates that lightweight typestate and capabilities are possible without runtime overhead explosion. See `src/main/java/com/kevel/util/Box.java`.

### 7. Assert preconditions, postconditions, and invariants aggressively.

Every important method should validate:

- inputs;
- output relationships;
- preserved invariants;
- bounds;
- assumptions at I/O and trust boundaries.

Rules:

- Assertions must be side-effect free.
- Split compound assertions into smaller assertions.
- Assert both positive space and negative space.
- For stateful objects, provide a `verifyInvariants()` helper in debug-oriented code paths.
- Add compile-time, startup-time, or static sanity checks for constant relationships.

Target: two meaningful assertions per important method. Use built-in Java helpers where appropriate (eg: `Objects.requireNonNull(...)`).

When formal checking matters, express these with JML contracts too:

- `requires`
- `ensures`
- `invariant`
- `assignable`

Reason: assertions turn vague intent into executable truth claims. They catch programmer mistakes early and amplify the power of fuzzing, property testing, and bounded checking.

### 8. Make arithmetic and units explicit.

- Never assume arithmetic cannot overflow.
- Use `Math.addExact`, `subtractExact`, `multiplyExact`, and exact conversions where overflow matters.
- Name units explicitly: `timeout_ms`, `size_bytes`, `count_max`.
- Distinguish index, count, size, and offset conceptually and in names.
- Make rounding behavior explicit.

Avoid casual mixing of `int` and `long`, or of indices and counts, especially in loops, slicing, and allocation logic.

Reason: off-by-one and overflow bugs are ordinary bugs, not edge-case curiosities.

### 9. Design for simulation, differential testing, and fault injection.

Every important subsystem should admit a small reference model and a production implementation that can be compared.

- Wrap time, randomness, disk, network, and external services behind interfaces.
- Keep core logic independent from direct I/O.
- Differential-test production against the model.
- Test histories, not only single calls.
- Inject failures deliberately: retries, partial progress, reordering, timeouts, corruption, crash/recovery.
- Turn every counterexample into a permanent regression test.

Suggested stack in this repo:

- JUnit for examples and regression tests
- jqwik for [generated histories](https://jqwik.net/docs/current/user-guide.html#stateful-testing) and [metamorphic tests](https://johanneslink.net/how-to-specify-it/#43-metamorphic-properties)
- Fray for concurrency testing / concurrency schedules
- OpenJML for method-level contracts and system invariants
- JBMC for bounded edge cases in critical kernels

Reason: the harness is the trust boundary. The model is useful only if it remains connected to the implementation.

### 10. Keep the codebase small in scope, strict in tooling, and explicit in rationale.

- Zero compiler warnings.
- Zero normalized analyzer debt.
- Prefer a small dependency set.
- Keep files small; target 500 lines or less per file.
- Keep variables in the smallest possible scope.
- Avoid duplicate state and semantic aliases.
- Explain why in comments, not what.
- Be explicit at call sites instead of relying on dangerous defaults.

Reason: safety, performance, and developer experience compound when the toolbox is small, the rules are strict, and rationale is preserved next to the code.

### 11. Concurrency is a design decision, not a convenience.

Do not add concurrency merely because the runtime makes it easy. Add concurrency only when the ownership model, ordering rules, and invariants are explicit.

- Prefer thread confinement, partitioned ownership, shared-nothing, queues, and single-writer regions over shared mutable state.
- Prefer stable partitions with one thread of execution per partition when the problem permits it. Eg: "One thread of awesome".
- Prefer message passing over lock-sharing in core stateful subsystems. Draw inspiration from replicated state machine design.
- Keep read and write paths conceptually distinct, with independent, isolated load characteristics.
- Bound queue depth, batch size, mailbox growth, retry count, and in-flight work.
- Make ordering guarantees explicit: total order, per-key order, causal order, or intentionally unordered.
- Do not hide concurrency behind ordinary-looking synchronous APIs if the scheduling semantics matter.
- If shared mutation is unavoidable, make the ownership boundary and synchronization protocol obvious in the type and API design.

Rules:

- Every concurrent subsystem must have a written statement of its ownership model.
- Every shared mutable structure must have named invariants and a clearly documented synchronization discipline.
- Every background task must have bounded work per unit of progress.
- Every cross-thread handoff must preserve validity of the receiving state.
- Every concurrency bug found in production or testing must become a deterministic regression test if at all possible.

When feasible, build concurrent systems so that any interleaving of truly independent events leads to the same valid end state (deterministic concurrency). Where this is not possible, make the serialization point explicit.

Suggested practice in this repo:

- Use Fray to exercise schedules and interleavings.
- Use jqwik to generate histories, not just single operations.
- Use JML contracts to pin down thread-confined state and postconditions at boundaries.
- Prefer immutable messages and records for cross-thread communication.

Reason: incidental concurrency destroys local reasoning. Deterministic or carefully partitioned concurrency preserves intellectual control, simplifies verification, improves replayability, and lowers long-term maintenance cost.

## High-Performance Java

High-performance Java begins with design, not micro-optimizations. The largest wins usually come from choosing the right data layout, ownership model, memory strategy, and control flow shape before the code is written. Prefer safety, explainable behavior and predictability first; throughput and latency usually follow.

### Core Principles

- **Allocation**: Minimize allocation and object churn. In hot paths, target zero allocation when practical; pre-allocate during startup and reuse memory deliberately.
- **Layout**: Design around data layout and access patterns, not only APIs. Prefer contiguous, cache-friendly layouts such as arrays, primitive arrays, and where appropriate, struct-of-arrays.
- **Dataflow**: Keep hot code simple and explicit so the JIT can inline, scalar-replace, and optimize it.
- **Bounded work**: Bound work and batch operations to amortize synchronization, parsing, system calls, and cache misses.
- **Hot-path isolation**: Separate hot paths from cold paths. Push dynamic behavior, branching, I/O, and abstraction overhead to the edges of the system and always outside inner loops.
- **Predictability**: Optimize for stable p95/p99 latency, not just peak throughput. Avoid unnecessary copies and move data only when the cost is justified.

### Mechanical Sympathy

When deciding how to implement something, reason from the hardware and runtime upward:

- **Caches and bandwidth**: Cache misses are often a real bottleneck. Favor compact data, sequential access, and layouts that keep the working set hot. Scattered reads, large copies, and oversized object graphs can saturate memory bandwidth before CPU.
- **Branching**: Unpredictable branches are expensive. Flatten hot-path conditionals and bias for the common case.  Use the `BL.java` utility if appropriate.
- **Escape analysis**: Local, non-escaping objects may be scalar-replaced by HotSpot; shared or escaping objects become real allocations.
- **Off-heap working sets**: Off-heap buffers can be useful as a controlled arena for performance-critical working data, especially when zero-copy semantics and tight memory control matter.
- **JIT friendliness**: Small, monomorphic methods inline best; deep abstraction stacks can block optimization. Stable shapes, `static final` constants, and simple control flow help HotSpot fold constants, simplify code paths, and optimize generated code.
- **Synchronization**: Contention, false sharing, and cross-core cache traffic are expensive. When needed, prefer partitioned ownership, one-writer designs, and lock-free algorithms.
- **Context switching**: Waking threads, bouncing work between executors, and oversharding can destroy throughput and tail latency. Reduce unnecessary handoffs.
- **Tail latency**: One surprise allocation, copy, blocking lock, or scheduler hop can dominate p95 and p99 behavior.

### Implementation Heuristics

- Prefer primitives over boxed types in hot code. Watch for accidental boxing in streams, lambdas, generics, and collections -- these can quietly dominate hot paths.
- Prefer arrays, primitive buffers, or struct-of-arrays layouts over nested object graphs when performance matters.
- Pre-allocate at startup when possible. When appropriate, use reusable objects, bounded pools, managed buffers, or arenas only when ownership, lifetime, and limits are explicit.
- Avoid copying. Prefer slices, views, flyweights, and zero-copy APIs when ownership remains clear.
- Consider off-heap buffers for performance-critical working sets that benefit from tighter memory control or zero-copy boundaries.
- Prefer `static final` constants for fixed limits, masks, shifts, lookup tables, and compile-time configuration.
- Prefer batching, exact bounds, explicit capacities, and simple handwritten loops in hot paths.

Suggested practice in this repo:

- JMH for micro-benchmarks and throughput/latency measurement
- `perf` and/or async-profiler for CPU and allocation profiling
- JFR (Java Flight Recorder) and JMC (Java Mission Control) for production-grade allocation, GC, and latency analysis

## Java 21 Guidelines

### Prefer sealed domains.

Use sealed interfaces and records for:

- command hierarchies;
- error types;
- protocol messages;
- parsed forms.

This makes `switch` exhaustive and forces the code to stay in sync with the domain.

### Prefer immutable values and value-oriented programming.

Prefer records and final fields for domain data. Mutate only where mutation is part of the design and the invariants are obvious.
Let functional programming patterns guide the way.  Prefer composing pure functions and methods.

### Prefer local reasoning.

Construct values close to where they are used. Avoid mutable state that survives across distant branches. Avoid temporal coupling.

### Prefer explicit ownership.

If state is shared, say so clearly. If it is confined to one thread, partition, actor, or scope, preserve that structure.

### Prefer total handling.

Every `switch` on a sealed type should feel like a proof that every case is handled.
Prefer switch expressions for all conditional handling.  `if` should only be used for single-branch conditionals.

### Always import

All classes and interfaces that are used in a Java file should be imported. Be explicit about class dependency even if the classes are in the same Java package.

### Interfaces isolate implementation decisions

Isolate the details of using specific libraries, technologies, or I/O interfaces behind an interface, such that the implementation decision can be changed without changing the integrated code. Always program to an interface, not an implementation.
These interfaces will later be used during testing to mock out details or to control fault injection.

### Documentation

Documentation is part of the safety case. It must make the intended semantics, constraints, and trust boundaries explicit enough for reviewers, maintainers, test authors, and verification tools to work from the same mental model.

#### Javadoc requirements

All classes and methods must have Javadoc.

For methods, Javadoc must document:

- preconditions;
- postconditions;
- preserved invariants;
- all exceptions that may be thrown;
- all safety requirements;
- any concurrency, ownership, ordering, or mutability assumptions;
- any bounds, units, or performance-sensitive behavior that callers must respect.

Use standard Javadoc tags where appropriate:

- `@param` for argument meaning and required properties
- `@return` for semantic meaning of the result
- `@throws` for all thrown exceptions and their conditions
- `@implNote` for implementation constraints that matter to maintainers
- `@apiNote` for usage guidance that matters to callers

If a method returns `Result<T, E>`, document the meaning of both success and error cases, including what invariants hold in each branch.

#### Examples

Include examples in:

- all class-level Javadocs;
- all public APIs that are non-trivial;
- any method whose behavior is subtle, stateful, capability-gated, performance-sensitive, or easy to misuse.

Examples should demonstrate correct usage, not merely compile.

#### Line comments

Use line comments to explain why the code is written the way it is, not to restate what the syntax already says.

Good line comments explain:

- why an invariant matters;
- why a bound exists;
- why a data layout was chosen;
- why a synchronization or ownership choice is safe;
- why a failure mode is handled in a particular way;
- why an optimization is correct and necessary.

#### Literate style for complex algorithms

For complex algorithms, parsers, protocol handlers, state transitions, concurrency logic, and performance-critical code, use line comments in a literate-programming style.  Walk through the logic in prose.

That means the code should read as a narrative:

1. state the goal of the step;
2. explain the invariant being established or preserved;
3. perform the step;
4. explain why the next step is safe.

The comment stream should help a careful reader follow the algorithm without reverse-engineering its intent from raw control flow.

#### Documentation rules

- Documentation must describe semantics, not merely surface syntax.
- Documentation must stay consistent with code, tests, and contracts.
- If a safety property matters, document it in both prose and executable form where possible.
- If a method has important preconditions or postconditions, prefer expressing them in both Javadoc and JML.
- If a comment becomes stale, fix or remove it immediately.
- Public APIs without adequate Javadoc are incomplete.

Reason: documentation preserves the mental model required for safe evolution. In a correctness-oriented codebase, Javadocs, contracts, examples, and literate comments are not decoration; they are part of the executable engineering record.

## Result and Exception Policy

Use this table:

| Situation | Preferred form |
|---|---|
| Validation failure | `Result<T, ValidationError>` |
| Parse failure | `Result<T, ParseError>` |
| Domain rule rejection | `Result<T, DomainError>` |
| Expected missing value | `Optional<T>`/`Box<Tag, T>` or `Result<T, E>` |
| Broken invariant | assertion or exception |
| Impossible state | exception |
| Unexpected I/O failure at boundary | checked/mapped exception or `Result` at boundary |

Rules:

- Do not throw to signal routine domain outcomes.
- Do not return `null` for failure.
- Do not use exceptions as hidden gotos.
- If a layer converts exceptions into `Result`, do it near the boundary and map them into domain errors.
- Result, Box, and Optional should only be used for return types, NEVER for input types or variable types.
- Core system logic should all expect and return non-null arguments. Handle null conditions near the boundary of the system.
- Perfer `Result<T, E>` or `Box<Tag, T>` over `Optional<T>`

## Box and Capability Policy

Use `Box<Tag, T>` when you need a lightweight logical type without changing the underlying representation.

Examples:

- authenticated vs unauthenticated session;
- validated vs unvalidated input;
- normalized vs raw data;
- readable file vs writable file;
- admin-authorized vs ordinary user;
- typed identifiers over primitive values or simple objects.

Rules:

- Prefer a tag over a comment.
- Prefer a witness when trust must survive a boundary.
- Keep witnesses private to the class that mints authority.
- Use `into()` only when the transition is valid by construction.
- Use `intoOnlyWith(...)` when a runtime proof of authority is required.

## Review Checklist

- Are the invariants stated clearly?
- Is the subsystem deterministic where possible and ideally modeled as a clear state machine?
- Are illegal states and illegal transitions hard to represent?
- Is control flow simple, explicit, and bounded?
- Are all expected failures returned as `Result`?
- Are exceptions reserved for exceptional conditions?
- Are preconditions, postconditions, and invariants asserted?
- Are Javadocs present and complete for classes and methods, including exceptions, safety requirements, and semantic constraints?
- Do line comments explain why the code is written this way, especially in subtle or performance-critical sections?
- Is arithmetic checked where overflow, rounding, or off-by-one errors matter?
- Are bounds explicit everywhere?
- Is core logic isolated from direct I/O, time, randomness, and external effects?
- Can the code be simulated, replayed, differentially tested, or fault-injected?
- Are histories tested, not just individual functions?
- Is concurrency explicit, bounded, and documented in terms of ownership and ordering?
- If state is shared, are the synchronization discipline and invariants obvious?
- Is the hot path allocation-free or close to it where performance matters?
- Are unnecessary copies avoided, and is the data layout cache-friendly?
- Is the "read" side decoupled from the "write" side?
- Are all warnings and analyzer checks clean?
- Would this design and its implementation still be easy to evolve in a year?

