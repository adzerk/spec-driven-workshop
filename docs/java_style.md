# Java Style Guide

A style guide for reliable, performant, evolvable Java systems.

This guide takes direct inspiration from [NASA's Power of 10](https://en.wikipedia.org/wiki/The_Power_of_10:_Rules_for_Developing_Safety-Critical_Code), [TigerStyle](https://tigerstyle.dev/), with additional influence from [Datadog](https://github.com/nerdsane/redis-rust/blob/main/docs/RUST_STYLE.md), Firecracker, DataFusion, and FoundationDB.

The priorities are:

1. Safety
2. Performance
3. Developer experience

Style is not decoration. Style is design pressure applied early enough to prevent defects, simplify reasoning, and lower maintenance cost.

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

Reason: deterministic [state machines](state-machines.md) are easier to reason about, test, replay, verify, and evolve. They also expose invariants clearly.

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

- Model `E` with an `enum` or sealed error hierarchy. `E` can also be an Exception to bridge worlds.
- Handle `Result` explicitly with `switch`, `map`, `flatMap`, or `fold`.
- Do not ignore `Result` values.
- Do not call `get()` on an `Err` in production logic.

Use exceptions only for:

- broken invariants;
- impossible states;
- programmer errors;
- infrastructure failures that are truly exceptional at the current layer.

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
- jqwik for generated histories and metamorphic tests
- Fray for concurrency schedules
- OpenJML for method-level contracts
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

## Java 21 Guidelines

### Prefer sealed domains.

Use sealed interfaces and records for:

- command hierarchies;
- state machines;
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
Prefer switch expressions for all conditional handling.

## Result and Exception Policy

Use this table:

| Situation | Preferred form |
|---|---|
| Validation failure | `Result<T, ValidationError>` |
| Parse failure | `Result<T, ParseError>` |
| Domain rule rejection | `Result<T, DomainError>` |
| Expected missing value | `Optional<T>` or `Result<T, E>` |
| Broken invariant | assertion or exception |
| Impossible state | exception |
| Unexpected I/O failure at boundary | checked/mapped exception or `Result` at boundary |

Rules:

- Do not throw to signal routine domain outcomes.
- Do not return `null` for failure.
- Do not use exceptions as hidden gotos.
- If a layer converts exceptions into `Result`, do it near the boundary and map them into domain errors.

## Box and Capability Policy

Use `Box<Tag, T>` when you need a lightweight logical type without changing the underlying representation.

Examples:

- authenticated vs unauthenticated session;
- validated vs unvalidated input;
- normalized vs raw data;
- readable file vs writable file;
- admin-authorized vs ordinary user;
- typed identifiers over primitive values.

Rules:

- Prefer a tag over a comment.
- Prefer a witness when trust must survive a boundary.
- Keep witnesses private to the class that mints authority.
- Use `into()` only when the transition is valid by construction.
- Use `intoOnlyWith(...)` when a runtime proof of authority is required.

## Review Checklist

- Are the invariants stated?
- Is the subsystem deterministic and ideally modeled as a clear state machine?
- Are illegal states hard to represent?
- Are all expected failures returned as `Result`?
- Are exceptions reserved for exceptional conditions?
- Are bounds explicit everywhere?
- Are assertions present for inputs, outputs, and invariants?
- Is arithmetic checked where overflow matters?
- Is core logic isolated from I/O?
- Is the "read" side decoupled from the "write" side?
- Can the code be simulated, replayed, or fault-injected?
- Are histories tested, not just individual functions?
- Is concurrency explicit and bounded?
- Are all warnings clean?
- Would this design still be easy to evolve in a year?

## Short Form

- Invariants first.
- Executable model.
- Deterministic core with state machines.
- Results over routine exceptions.
- Types over flags.
- Bounds over hope.
- Assertions over comments.
- Determinism over incidental concurrency.
- Differential testing over intuition.
- Simplicity over cleverness.
