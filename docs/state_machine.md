# State Machines in Java

State machines are a strong foundational construction pattern in software
because they make legal behavior explicit. State machines are useful in
production systems because they make legal behavior explicit and easier to
enforce, debug, and test.

Instead of scattering lifecycle rules across conditionals, a state machine
names the valid states, the allowed transitions, and the actions that move the
system forward.

## Why use state machines?

- They make workflows explicit. A reader can see the lifecycle directly instead of inferring it from flags and `if` statements.
- They reduce invalid states. When the model says an object is `Draft`, `Paid`, or `Shipped`, the code can avoid impossible combinations.
- They improve maintainability. Adding a new state or transition becomes a focused change to the model instead of a hunt across the codebase.
- They support better testing. It is easier to enumerate expected transitions and terminal states.
- They align well with formal reasoning. Preconditions, postconditions, and invariants often map naturally onto state transitions.
- They can move correctness earlier. With the right Java design, some invalid transitions can be rejected by the compiler instead of failing at runtime.

## Quick comparison

| Approach | Compiler-checked transitions | Allocation profile | Best for | Main tradeoff |
|---|---|---|---|---|
| `enum` | No | Low to moderate | Simple business workflows | Mostly runtime-checked |
| Typestate | Yes | Moderate by default, low with care | APIs, protocols, capability-style workflows | More generic machinery |
| Sealed state types | Yes | Moderate | Clear domain models, JML-friendly code | More types and boilerplate |
| Packed primitives | Limited | Very low | Hot paths, lowest latency, lowest GC | Harder to read and evolve |
| Singleton typestate | Yes | Very low | Hot paths that still need typed transitions | More complex than wrapper typestate |

## Common ways to implement state machines in Java

### 1. Enum-based state machines

This is the simplest approach. States are enum constants, and transition logic usually lives in methods on the enum or in a separate transition function.

Advantages:

- Compact and familiar.
- Easy to serialize, log, and inspect.
- Good for simple business workflows.

Tradeoffs:

- Transition legality is mostly enforced at runtime.
- Simple enum designs often model a single default `next` state; branching usually requires extra methods or external policy.
- The compiler knows the set of states, but usually cannot prevent illegal transition calls.

Example:

```java
package com.kevel.examples;

public final class EnumOrderExample {
    enum OrderState {
        DRAFT {
            @Override
            OrderState next() {
                return PAID;
            }
        },
        PAID {
            @Override
            OrderState next() {
                return SHIPPED;
            }
        },
        SHIPPED {
            @Override
            OrderState next() {
                throw new IllegalStateException("SHIPPED is terminal");
            }
        };

        abstract OrderState next();
    }

    static final class Order {
        private final String id;
        private OrderState state;

        Order(String id) {
            this.id = id;
            this.state = OrderState.DRAFT;
        }

        void advance() {
            state = state.next();
        }

        OrderState state() {
            return state;
        }
    }
}
```

### 2. Typestate with generics or phantom types

In this design, the state appears in the Java type. A value like `Order<Draft>` can only be passed to operations that accept the `Draft` state, and each transition returns a value with a different type parameter such as `Order<Paid>`.

Advantages:

- Strong compile-time enforcement.
- Good fit for APIs, protocols, and workflows.
- Makes illegal transitions literally fail to compile.

Tradeoffs:

- More verbose than enums.
- Can become awkward for highly branching or cyclic graphs.

This repository's `Box<Tag, T>` utility in `src/main/java/com/kevel/util/Box.java` is a useful lightweight tagged wrapper for this style.

Example:

```java
package com.kevel.examples;

import com.kevel.util.Box;

public final class TypestateOrder {
    interface Draft {}
    interface Paid {}
    interface Shipped {}

    record OrderData(String id, int cents) {}

    public static final class Order<S> {
        private final Box<S, OrderData> data;

        private Order(Box<S, OrderData> data) {
            this.data = data;
        }

        public OrderData data() {
            return data.get();
        }
    }

    public static Order<Draft> create(String id, int cents) {
        return new Order<>(Box.of(new OrderData(id, cents)));
    }

    public static Order<Paid> pay(Order<Draft> draft) {
        return new Order<>(draft.data.into());
    }

    public static Order<Shipped> ship(Order<Paid> paid) {
        return new Order<>(paid.data.into());
    }

    public static void main(String[] args) {
        Order<Draft> draft = create("o-123", 2500);
        Order<Paid> paid = pay(draft);
        Order<Shipped> shipped = ship(paid);

        // Does not compile:
        // Order<Shipped> bad = ship(draft);
    }
}
```

### 3. Sealed interfaces and records with explicit transition methods

In Java 21, sealed types provide a closed set of states. Each state is a distinct type, and only that state exposes the transitions that are valid from it.

Advantages:

- Very readable model of the lifecycle.
- Strong compile-time guarantees.
- Exhaustive `switch` support over the sealed hierarchy.

Tradeoffs:

- More types to define.
- Can be verbose for large state graphs.

Example:

```java
package com.kevel.examples;

public final class SealedOrderExample {
    sealed interface OrderState permits DraftOrder, PaidOrder, ShippedOrder {
        String id();
        int cents();
    }

    record DraftOrder(String id, int cents) implements OrderState {
        PaidOrder pay() {
            return new PaidOrder(id, cents);
        }
    }

    record PaidOrder(String id, int cents) implements OrderState {
        ShippedOrder ship() {
            return new ShippedOrder(id, cents);
        }
    }

    record ShippedOrder(String id, int cents) implements OrderState {}

    public static void main(String[] args) {
        DraftOrder draft = new DraftOrder("o-123", 2500);
        PaidOrder paid = draft.pay();
        ShippedOrder shipped = paid.ship();

        // Does not compile:
        // draft.ship();
    }
}
```

### 4. Packed-primitive state machines

In this design, the machine state is stored in one or a few primitive values, often a single `long`, and transitions are implemented as small `static final` helper methods that unpack, update, and repack the bits.

Advantages:

- Very low allocation and GC pressure.
- Often the best raw throughput and latency profile.
- A good fit for hot loops and protocol engines.

Tradeoffs:

- Harder to read and debug than object-based designs.
- Compiler-checked transition safety is weaker unless wrapped by a typed API.
- Bit layouts must be designed and maintained carefully.

Example:

```java
package com.kevel.examples;

public final class PackedOrderExample {
    private static final long STATE_MASK = 0x3L;
    private static final int REVISION_SHIFT = 2;
    private static final int CENTS_SHIFT = 18;
    private static final int ID_SHIFT = 34;

    private static final int DRAFT = 0;
    private static final int PAID = 1;
    private static final int SHIPPED = 2;

    static long create(long id, int cents, int revision) {
        return ((id & 0x3FFFFFFFL) << ID_SHIFT)
                | (((long) cents & 0xFFFFL) << CENTS_SHIFT)
                | (((long) revision & 0xFFFFL) << REVISION_SHIFT)
                | DRAFT;
    }

    static long pay(long order) {
        return (order & ~STATE_MASK) | PAID;
    }

    static long ship(long order) {
        return (order & ~STATE_MASK) | SHIPPED;
    }
}
```

### 5. Singleton typestate

Singleton typestate separates protocol safety from runtime data. Legal states are represented by reused singleton state objects or state classes, while runtime data lives in a mutable primitive carrier or packed `long`.

Advantages:

- Compiler-checked transitions.
- Very low allocation when paired with packed or mutable primitive data.
- Useful middle ground between packed primitives and wrapper-based typestate.

Tradeoffs:

- More complex than ordinary typestate wrappers.
- Less explicit than sealed state objects.
- Shared mutable carriers require synchronization or thread confinement.

Example:

```java
package com.kevel.examples;

public final class SingletonTypestateOrder {
    static final class OrderData {
        long id;
        int cents;
        int revision;
    }

    static final Draft DRAFT = new Draft();
    static final Paid PAID = new Paid();
    static final Shipped SHIPPED = new Shipped();

    static final class Draft {
        private Draft() {}

        Paid pay(OrderData data) {
            return PAID;
        }
    }

    static final class Paid {
        private Paid() {}

        Shipped ship(OrderData data) {
            return SHIPPED;
        }
    }

    static final class Shipped {
        private Shipped() {}

        Draft reset(OrderData data) {
            data.revision++;
            return DRAFT;
        }
    }
}
```

## Choosing an approach

- Use `enum` when simplicity matters more than compile-time transition safety.
- Use typestate when the workflow itself is part of the API contract.
- Use sealed state types when you want a clear, explicit, domain-centered model.
- Use packed primitives when absolute throughput and minimal allocation are the priority.
- Use singleton typestate when you want near-zero-allocation code with compiler-checked transitions.

## Practical guidance

For many Java systems, the best progression is:

1. Start with an enum if the lifecycle is simple.
2. Move to sealed types when state-specific behavior starts to grow.
3. Use typestate when illegal transitions must be caught by the compiler.
4. Use singleton typestate or packed primitives when the machine sits on a hot path.

If the software is already using contracts, invariants, or JML, state machines fit naturally:
each transition can be described by preconditions and postconditions, and the state model becomes a clean place to express those guarantees.

## Production concerns

In production systems, the best state-machine design is not just the one with the strongest type story. It also needs to work well with storage, concurrency, observability, and long-term evolution.

- Persist logical state, not Java implementation details. Store state names or stable numeric codes in databases and messages.
- Validate external input at runtime before converting it into stronger typed internal representations.
- Be explicit about concurrency. Immutable wrapper designs are easier to share; mutable packed or singleton-carrier designs usually rely on thread confinement.
- Add structured logging around state transitions so production failures can be traced in terms of domain states, not only internal classes or bit patterns.
- Plan for evolution. Adding a new state is easier when state handling is centralized and exhaustive.

## Using JML with state machines

This section is optional for teams that are not using JML or OpenJML.

JML fits state machines well because transitions already look like contracts. In practice, transition legality maps to `requires`, destination state and preserved data map to `ensures`, modified fields map to `assignable`, and always-true lifecycle properties map to `invariant` clauses.

### Main JML guidance

- Use `requires` for legal transitions.
- Use `ensures` for the destination state and preserved business data.
- Use `assignable` to keep frame conditions tight.
- Use `invariant` for properties that must hold in every visible state.
- Prefer block comment syntax such as `/*@ ... @*/`, since formatters can silently break single-line `//@` annotations.

### JML with an enum-based state machine

An enum-based machine usually stores the current state in a mutable field. JML works well here to verify runtime guards and effects, even though the Java type system does not prevent illegal transition calls on its own.

```java
public final class Order {
    public enum State { DRAFT, PAID, SHIPPED }

    private /*@ spec_public @*/ State state;
    private /*@ spec_public @*/ String id;
    private /*@ spec_public @*/ int cents;

    /*@ public invariant state != null; @*/
    /*@ public invariant cents >= 0; @*/

    /*@ public normal_behavior
      @   requires state == State.DRAFT;
      @   assignable state;
      @   ensures state == State.PAID;
      @   ensures id.equals(\old(id));
      @   ensures cents == \old(cents);
      @*/
    public void pay() {
        state = State.PAID;
    }

    /*@ public normal_behavior
      @   requires state == State.PAID;
      @   assignable state;
      @   ensures state == State.SHIPPED;
      @*/
    public void ship() {
        state = State.SHIPPED;
    }
}
```

This style is useful when the lifecycle is simple and runtime checks are acceptable. JML makes the transition rules explicit and machine-checkable, but the compiler still cannot reject all illegal calls ahead of time.

### JML with typestate or phantom types

Typestate moves part of the protocol into Java's type system. For example, `pay` accepts `Order<Draft>` and returns `Order<Paid>`. That means some transition legality is already compiler-checked before JML enters the picture.

JML is still useful here, but it mainly specifies data preservation, object validity, and factory or helper behavior. OpenJML has only partial support for complex generics, so simple typestate APIs tend to work better than deeply generic ones.

For deeper JML guidance, see the appendix at the end of this document.

### JML with sealed state types

Sealed state types are often the best match for JML. Each state is a distinct Java type, and each legal transition can live directly on the source state type.

This works well with JML because:

- illegal transitions are absent from the method surface,
- each method contract is local and simple,
- specifications can talk about concrete source and destination types,
- and exhaustive handling over the sealed hierarchy remains available.

Conceptually, the shape is straightforward:

```java
final class JmlSealedOrderSketch {
    sealed interface OrderState permits DraftOrder, PaidOrder, ShippedOrder {}

    static final class DraftOrder implements OrderState {
        private final String id;
        private final int cents;

        DraftOrder(String id, int cents) {
            this.id = id;
            this.cents = cents;
        }

        /*@ public normal_behavior
          @   requires cents >= 0;
          @   assignable \nothing;
          @   ensures \result != null;
          @   ensures \typeof(\result) == \type(PaidOrder);
          @*/
        public /*@ pure @*/ PaidOrder pay() {
            return new PaidOrder(id, cents);
        }
    }

    static final class PaidOrder implements OrderState {
        private final String id;
        private final int cents;

        PaidOrder(String id, int cents) {
            this.id = id;
            this.cents = cents;
        }
    }

    static final class ShippedOrder implements OrderState {}
}
```

In practice, this style keeps JML contracts short and aligned with the domain model. Compared with typestate, it usually asks less of OpenJML's generic reasoning.

### Which pattern works best with JML?

For most JML-heavy Java code, the best fit is usually sealed state types, followed by simple enum-based machines, and then typestate.

- Sealed state types are usually the strongest overall combination of clarity, compiler checking, and OpenJML friendliness.
- Enum-based machines are easy to specify and verify, but rely more on runtime guards than on type-level safety.
- Typestate provides the strongest Java-level protocol safety, but can be harder for OpenJML when generics become complex.

If the goal is to combine Java's type system with JML contracts, a useful rule of thumb is:

1. Use sealed state types when you want the clearest JML specifications.
2. Use typestate when compile-time transition safety matters most and the generic design stays simple.
3. Use enums when the machine is small and mutable state is acceptable.

### Additional practical notes

- Prefer abstract model fields if public specs should describe logical state instead of concrete representation.
- Use `spec_public` sparingly; it is convenient, but model fields are usually a cleaner abstraction boundary.
- Keep transition methods small. Small transition bodies are easier for OpenJML to verify.
- If a transition allocates a new object, specify both the result type and the preserved data properties.
- If a transition can fail by throwing, use `behavior` with `signals` and `signals_only` instead of relying only on `normal_behavior`.

The practical rule of thumb is simple: use sealed state types when JML clarity matters most, use typestate when compiler-checked transitions matter most and the generic design stays simple, and use enums when runtime-checked state is sufficient.

## Coupled machines: product-state encoding

Some systems are not a single state machine, but several machines whose states
must stay consistent with each other. In those cases, it is often better to
model the combined system directly as a product state machine instead of
keeping separate mutable machines and trying to synchronize them with runtime
checks.

The traffic light intersection is a good example. Suppose the legal combined states are:

- `GR`: north-south is green, east-west is red
- `YR`: north-south is yellow, east-west is red
- `RR`: both directions are red
- `RG`: north-south is red, east-west is green
- `RY`: north-south is red, east-west is yellow
- `FF`: both directions are flashing red because of a fault

The key idea is that the type represents the whole intersection, not each light independently. That makes illegal combinations unrepresentable.

Ideal implementation:

```java
package com.kevel.examples;

public final class CoupledTrafficLights {
    interface Green {}
    interface Yellow {}
    interface Red {}
    interface FlashingRed {}

    sealed interface Intersection<A, B>
            permits GR, YR, RR, RG, RY, FF {}

    record GR() implements Intersection<Green, Red> {
        YR nextState() {
            return new YR();
        }
    }

    record YR() implements Intersection<Yellow, Red> {
        RR nextState() {
            return new RR();
        }
    }

    record RR() implements Intersection<Red, Red> {

        // When both lights are red, the side with more traffic gets Green

        GR northSouthFirst() {
            return new GR();
        }

        RG eastWestFirst() {
            return new RG();
        }

        <T> T dispatch(Choice<T> choice) {
            return choice.fromRedRed(this);
        }
    }

    record RG() implements Intersection<Red, Green> {
        RY nextState() {
            return new RY();
        }
    }

    record RY() implements Intersection<Red, Yellow> {
        RR nextState() {
            return new RR();
        }
    }

    record FF() implements Intersection<FlashingRed, FlashingRed> {
        RR restore() {
            return new RR();
        }
    }

    interface Choice<T> {
        T fromRedRed(RR rr);
    }

    static RR init() {
        return new RR();
    }

    static FF fault(Intersection<?, ?> ignored) {
        return new FF();
    }

    static void example() {
        var normalCycle = init()
                .northSouthFirst()
                .nextState()
                .nextState()
                .eastWestFirst() // East-West has more traffic
                .nextState()
                .nextState();

        var withTypedBranch = init()
                .dispatch(RR::eastWestFirst)
                .nextState()
                .nextState();

        var afterFault = fault(normalCycle).restore().northSouthFirst();
    }
}
```

This pattern adds two useful ideas on top of ordinary state machines.

### Product-state encoding

The combined type `Intersection<A, B>` is a product of two component lights. But callers never construct arbitrary pairs. Instead, they work only with the finite set of legal concrete states: `GR`, `YR`, `RR`, `RG`, `RY`, and `FF`.

That is often the right pattern when:

- local state is constrained by a global invariant,
- two actors must transition in lockstep,
- or a coordinator must enforce safety across several subsystems.

### Controlled branching via type dispatch

Some states have more than one legal next state. In the traffic light example, `RR` may hand control to either direction. That is not ordinary nondeterminism; it is controlled branching.

There are two clean ways to express that:

- expose named transition methods such as `northSouthFirst()` and `eastWestFirst()`,
- add a typed dispatch method such as `dispatch(Choice<T>)` so callers can choose among the legal branches while the compiler still rejects impossible ones.

The chained invocation style remains natural:

```java
package com.kevel.examples;

public final class CoupledTrafficLightsDispatchExample {
    static void example() {
        var state = CoupledTrafficLights.init()
                .dispatch(CoupledTrafficLights.RR::eastWestFirst)
                .nextState()
                .nextState();

        System.out.println(state);
    }
}
```

That chaining is useful because each call narrows the type of the next step. The API reads like a protocol, and the compiler keeps the whole chain honest.

### When this pattern is a good fit

- Use product-state encoding when invariants span more than one entity.
- Use explicit concrete state types when illegal combinations must be impossible to construct.
- Use controlled branching when some states have a small, closed set of legal successor states.

This pattern is usually best implemented with sealed state types and explicit transition methods. It also fits JML well, because contracts can be attached to each legal transition locally.

## Capabilities as state machines

Capabilities can be modeled as a state machine too. This is a specialized form
of typestate. Instead of saying an object is in state `Draft` or `Paid`, we say
a value carries a capability such as `Authenticated`, `MfaVerified`, or
`AdminApproved`. Each transition grants, refines, or revokes what the caller is
allowed to do next.

`Box<Tag, T>` in `src/main/java/com/kevel/util/Box.java` is a lightweight way
to express this style. The payload `T` is the real runtime value, and the tag
is compile-time state. Because `Box` is immutable and `into()` changes only the
tag, it works well for typestate and capability-oriented APIs.

Example:

```java
package com.kevel.examples;

import com.kevel.util.Box;

public final class SessionCapabilities {
    interface Anonymous {}
    interface Authenticated {}
    interface MfaVerified extends Authenticated {}
    interface LoggedOut {}

    record Session(String userId) {}
    record TransferRequest(String from, String to, long cents) {}

    static Box<Anonymous, Session> begin(String userId) {
        return Box.of(new Session(userId));
    }

    static Box<Authenticated, Session> login(Box<Anonymous, Session> session, String password) {
        return session.into();
    }

    static Box<MfaVerified, Session> verifySecondFactor(Box<Authenticated, Session> session, String code) {
        return session.into();
    }

    static <T extends Authenticated> Box<LoggedOut, Session> logout(Box<T, Session> session) {
        return session.into();
    }

    static String readProfile(Box<Authenticated, Session> session) {
        return "profile for " + session.get().userId();
    }

    static void transferMoney(Box<MfaVerified, Session> session, TransferRequest request) {
        if (request.cents() <= 0) {
            throw new IllegalArgumentException("cents must be positive");
        }
    }

    static void example() {
        var session = begin("alice");
        var authenticated = login(session, "correct horse battery staple");
        var profile = readProfile(authenticated);
        var elevated = verifySecondFactor(authenticated, "123456");
        transferMoney(elevated, new TransferRequest("checking", "savings", 5_000));
        var loggedOut = logout(elevated);

        System.out.println(profile + " -> " + loggedOut);

        // Does not compile:
        // transferMoney(authenticated, new TransferRequest("checking", "savings", 5_000));
    }
}
```

This pattern is useful because the capability itself becomes the transition guard. The method signature says what proof of authority is required.

### Why this is a state machine

The session evolves through a lifecycle:

- `Anonymous`
- `Authenticated`
- `MfaVerified`
- `LoggedOut`

Those are states. The operations `login`, `verifySecondFactor`, and `logout` are transitions. The capability tags are simply a compact way to encode that machine in Java's type system.

### Where `Box` fits well

`Box<Tag, T>` is especially useful when:

- the runtime value is simple and should not be wrapped in a large domain hierarchy,
- the state matters mainly for compile-time protocol checking,
- and the transition API can be expressed as tag changes over the same payload.

This makes `Box` a good fit for:

- capability-gated service calls,
- request processing pipelines,
- authenticated session lifecycles,
- and lightweight typestate over values like `Path`, `String`, `Map`, or records.

### Trust and witnesses

Plain `Box.of(value)` plus `into()` is enough when the goal is compile-time
discipline inside a trusted module. If the capability must also be protected
against forgery across trust boundaries, `Box.of(value, witness)` and
`hasWitness(...)` can add a runtime proof that the capability originated from
approved code.

Very small example:

```java
package com.kevel.examples;

import com.kevel.util.Box;

public final class WitnessedSessionCapabilities {
    interface Anonymous {}
    interface Authenticated {}

    record Session(String userId) {}

    private static final Object WITNESS = new Object();

    static Box<Anonymous, Session> begin(String userId) {
        return Box.of(new Session(userId), WITNESS);
    }

    static Box<Authenticated, Session> login(Box<Anonymous, Session> session, String password) {
        if (!session.hasWitness(WITNESS)) {
            throw new SecurityException("Untrusted session");
        }
        return session.into(WITNESS);
    }
}
```

This example carries the same witness forward during `login(...)` by using
`into(WITNESS)`.

That leads to a useful rule of thumb:

- use tags alone for lightweight internal typestate,
- use tags plus witnesses for trusted capability minting,
- and use dedicated state classes when behavior differs substantially by state.

## Performance Considerations

State-machine performance in Java depends less on the abstract pattern name and more on the runtime shape of the implementation.

- The fastest designs usually minimize heap allocation, object churn, and unpredictable branching.
- Small `static final` helpers and compact object shapes are generally good for inlining and JIT optimization.
- A "stateless" design is only low-overhead if the state is carried in cheap values such as primitives or packed data, not if each transition allocates a fresh wrapper object.
- Benchmark hot-loop and escaping cases separately. A design can look fast when the JVM keeps values local, but behave differently when intermediate states must be stored or observed.

### Why typestate is often a strong performance choice

Typestate is a particularly attractive low-overhead option in Java because the state marker lives in the type system.

- Generic type parameters are erased at runtime, so `Order<Draft>` and `Order<Paid>` are the same runtime class shape.
- That means the compiler-checked state marker itself is essentially free at runtime.
- The remaining cost comes from the payload and wrapper objects you choose to allocate, not from the phantom type.
- In practice, typestate can be close to the cost of other lightweight immutable wrappers while giving much stronger compile-time guarantees.

This is why `Box<Tag, T>` works well as a typestate utility in this repository: the tag is compile-time only, and the runtime object is still just a small immutable wrapper around `T`.

### Common performance pitfalls

- Avoid assuming that all immutable or stateless designs are automatically fast.
- A stateless API that returns a new record or wrapper on every transition may allocate heavily.
- Carrying an explicit runtime `enum` field in each wrapper can be more expensive than encoding the state in the type parameter.
- Sealed-state models are often very clear, but may allocate a new object per transition unless carefully designed.

### Practical guidance for low-overhead implementations

- Use `enum` plus mutation when raw simplicity and minimal allocation matter more than compile-time transition safety.
- Use typestate when you want compiler-checked transitions with very little intrinsic runtime overhead.
- Use sealed state types when clarity, explicit behavior, or JML friendliness matter more than minimizing wrapper churn.
- If throughput and latency are critical, consider a truly low-level stateless design based on primitive arguments, packed state, and `static final` transition functions.
- If you use typestate or sealed wrappers, keep the object shape small and the transition bodies tiny so the JVM has the best chance to inline and optimize aggressively.

### Packed primitives

Another important implementation style is a packed-primitive state machine. In this design, the machine state is stored in one or a few primitive values, often a single `long`, and transitions are implemented as `static final` helper methods that unpack, update, and repack the bits.

- This style is effectively allocation-free in steady-state code because transitions operate on primitives instead of allocating wrapper objects.
- It is often the best choice when throughput, latency, and GC avoidance matter more than rich object modeling.
- It works especially well for tight loops, stream processors, protocol engines, schedulers, embedded-style logic, and other hot paths.
- It also makes escape cases cheaper, because state can be stored in primitive arrays such as `long[]` instead of object collections.

The tradeoff is readability and ergonomics.

- Packed state is less self-documenting than typestate or sealed classes.
- Bit layout must be designed carefully and maintained consistently.
- Debugging and ad hoc inspection are usually harder.
- Compiler-checked transition safety is weaker unless the packed representation is wrapped behind a carefully designed API.

In benchmark terms, this style is the "performance floor" for state machines in Java. The packed-`long` benchmarks in `src/jmh/java/com/kevel/bench/state/` were the fastest implementations in both the simple and robust scenarios, and they were effectively allocation-free with near-zero `B/op` and almost no GC activity.

As a rule of thumb, choose packed primitives when:

- the machine sits on a hot path,
- object allocation or GC pauses are a real concern,
- the state graph is stable and well understood,
- and the team is comfortable trading some readability for performance.

If those conditions do not hold, typestate is often a better default because it preserves most of the performance discipline while remaining much easier to read, evolve, and use safely.

### Singleton typestate

Singleton typestate is a useful middle ground between packed primitives and ordinary typestate wrappers. It separates protocol safety from runtime data.

- Legal states are represented by reused singleton state objects or state classes.
- Runtime data lives in a mutable primitive carrier or packed `long`.
- Transitions update the carrier and return the next singleton state type.
- This preserves compiler-checked transition safety without allocating a fresh wrapper on each transition.

This style performs well because it removes most per-transition allocation while still encoding legal transitions in Java's type system.

- It avoids wrapper churn.
- It keeps the state tokens effectively free at runtime.
- When paired with packed primitives, it can be nearly allocation-free and generate almost no GC pressure.
- In the benchmarks in `src/jmh/java/com/kevel/bench/state/`, singleton typestate stayed very close to the packed-primitive floor while retaining stronger compile-time guarantees.

Choose singleton typestate when:

- the machine runs on a hot path,
- illegal transitions should still fail at compile time,
- the code can tolerate a mutable carrier or thread-confined packed state,
- and throughput or latency matter more than maximizing readability.

Tradeoffs:

- More complex than ordinary wrapper-based typestate.
- Less readable than sealed state objects.
- Requires careful separation of typed state from mutable data.
- Thread safety depends on ownership of the carrier; shared mutable carriers need synchronization.

### Benchmark sources

The performance guidance in this section is grounded in the JMH benchmarks in `src/jmh/java/com/kevel/bench/state/SimpleStateMachineBenchmark.java` and `src/jmh/java/com/kevel/bench/state/RobustStateMachineBenchmark.java`. Shared implementations and fixtures live in `src/jmh/java/com/kevel/bench/state/StateMachineBenchmarkSupport.java`.

### Rule of thumb

If you want the best balance of correctness and performance in ordinary Java application code, wrapper-based typestate is often the sweet spot:

- stronger guarantees than enums,
- usually less runtime baggage than richer runtime-tagged wrappers,
- and a much lower conceptual and runtime cost than many developers expect.

For performance-oriented systems, a practical ladder is:

- packed primitives for absolute speed,
- singleton typestate for near-zero-allocation code with compiler-checked transitions,
- wrapper typestate for the best everyday balance of safety and simplicity,
- sealed state objects for the clearest domain model.

## Appendix: Deeper JML guidance

The main JML section above focuses on how state machines map naturally to contracts. This appendix collects the deeper guidance that is most useful when you are writing production specifications.

### Method-level contracts for transitions

- Use `requires` to describe when a transition is legal.
- Use `ensures` to describe the destination state and preserved business data.
- Use `assignable` to keep frame conditions tight.
- Use `\old(...)` for values that must remain unchanged.
- Use class `invariant`s for facts that must hold in every visible state.

### Recommended JML style for state machines

- Prefer block comments such as `/*@ ... @*/` over single-line `//@` annotations, since formatters can silently break the single-line form.
- Keep transition methods small and local. Smaller methods are easier for OpenJML to verify.
- Prefer abstract model fields if public specifications should describe logical state instead of concrete representation.
- Use `spec_public` sparingly; it is convenient, but it leaks representation details into public contracts.

### Pattern guidance

- Enum-based machines are straightforward to specify, but most transition legality still comes from runtime checks and JML preconditions.
- Sealed state types are often the best match for JML because each legal transition can live on the source state type and be specified locally.
- Typestate can work well with JML, but complex generics may be harder for OpenJML to reason about than for `javac`.

### Practical production advice

- Use JML to specify business guarantees, not just internal implementation details.
- Prefer contracts that describe logical state transitions and preserved invariants.
- If a transition can fail by throwing, use `behavior` with `signals` and `signals_only` rather than relying only on `normal_behavior`.
- If a transition allocates a new object, specify both the result type and the key preserved properties.

### Summary

When JML is part of the toolchain, state machines become an especially strong design choice:

- Java types can prevent some illegal transitions,
- JML can describe the legal transitions precisely,
- and invariants can capture the guarantees that must hold in every state.
