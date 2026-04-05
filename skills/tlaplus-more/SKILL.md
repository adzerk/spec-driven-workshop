---
name: tlaplus-more
description: Write and verify formal TLA+ and PlusCal specifications for system designs, state machines, algorithms, and concurrent protocols. Covers invariants, temporal properties, model checking, nondeterminism, fairness, and optimization. (more details)
license: MIT
compatibility: opencode
metadata:
  domain: formal-methods
  languages: tla-plus,pluscal
---

# TLA+ Specification Skill

Write TLA+ and PlusCal specifications. Verify system designs and algorithm correctness with the TLC model checker.

## MANDATORY Rules

- Never say "proved correct". Say "no counterexample found" and state the bounds/model used.
- Always surface modeling assumptions you introduced to remove ambiguity.
- If liveness is in scope, explicitly state fairness assumptions used in the run (`WF_`/`SF_`), or explicitly say "none (safety-only run)".
- Prefer `WF_` (weak fairness) by default. Only use `SF_` (strong fairness) when an action is repeatedly enabled and disabled (e.g., competing for a shared resource). If using `SF_`, document why `WF_` is insufficient.
- Apply fairness only to system/engine actions, not to environment actions. If `Schedule` is triggered by an external caller, `WF_vars(Schedule)` forces the environment to eventually schedule, which is usually not a valid assumption. Environment liveness should be modeled as an explicit assumption separate from the spec's fairness.
- Actively guard against vacuous success before calling a run "pass":
  - Show that at least one non-stuttering transition is reachable.
  - If using `CONSTRAINT` / `ACTION_CONSTRAINT`, list each one and the behavior it excludes.
  - Reject properties that are tautological or trivially weakened.
  - If any vacuity check is inconclusive, report "inconclusive coverage" instead of "pass".

## What I Do

- Write TLA+ and PlusCal specifications for systems, algorithms, state machines, and protocols
- Define invariants (safety properties) and temporal properties (liveness)
- Model concurrency, nondeterminism, race conditions, and deadlocks
- Suggest model configurations (constants, invariants, temporal properties)
- Optimize specifications for efficient model checking

## When to Use

### System design
- Documenting a system or process as a state machine
- Specifying the intended behavior of a protocol before implementation

### Concurrent systems
- Concurrent actors sharing mutable state (offline queues, sync engines, caches)
- Distributed systems (client-server sync, multi-device, event sourcing)
- Multi-agent orchestration (swarm agents, parallel worktree operations)
- Any system where "it works in my test" is insufficient because interleavings matter

### Algorithm invariant checking
- Pure functions with complex input spaces (recursive traversals, tree walkers, parsers)
- Functions with multiple code paths that must produce consistent results
- Algorithms where "for all possible inputs, property X must hold"
- Migration/transformation logic where data must never be lost or corrupted
- Any code where you suspect a subtle edge case but can't enumerate all inputs by hand

## When NOT to Use

- Pure CRUD with no concurrency or complex logic
- Simple request-response APIs with no shared state
- UI layout or styling questions
- Runtime verification or testing code (use a test framework instead)
- Production code in any programming language
- Anything requiring floating-point arithmetic (TLA+ has no float type)

---

## Workflow

When creating TLA+ specifications:

1. **Clarify the system.** Identify state variables, agents/processes, and key properties.
2. **Choose PlusCal vs pure TLA+.** PlusCal for sequential/concurrent algorithms. Pure TLA+ for state machines, complex fairness, refinement, or systems that don't map cleanly onto sequential processes.
3. **Define constants and variables.** Establish the state space. Add `ASSUME` for every constant documenting valid values.
4. **Write the algorithm/actions.** Model the behavior. One action per atomic step -- if two things can't happen simultaneously in the real system, they are separate actions.
5. **Write a TypeInvariant first.** Bounds-check every variable. This should pass before writing complex properties.
6. **Add correctness invariants (safety).** What must always be true?
7. **Add temporal properties (liveness).** What must eventually happen? State the fairness model that applies to each action (`WF_`/`SF_`), or explicitly note "none (safety-only run)".
8. **Suggest a model configuration.** Constant values, which invariants/properties to check, symmetry sets.
9. **Model check with TLC.** Run with small constants first. Iterate quickly, then scale up.
10. **Refine.** Add detail, fix discovered bugs, verify non-vacuity before declaring "pass".

---

## Common Patterns

### State Machines in PlusCal

Use `either/or` with `await` guards:

```tla
(* --algorithm lamp
variable state = "BothOff";
begin
  Action:
    either
      await state = "BothOff"; state := "WallOff";
    or
      await state = "BothOff"; state := "LampOff";
    or
      await state = "WallOff"; state := "On";
    or
      await state = "WallOff"; state := "BothOff";
    or
      await state = "LampOff"; state := "On";
    or
      await state = "LampOff"; state := "BothOff";
    or
      await state = "On"; state := "WallOff";
    or
      await state = "On"; state := "LampOff";
    end either;
    goto Action;
end algorithm; *)
```

### State Machines in Pure TLA+

Cleaner with a helper:

```tla
VARIABLE state

Trans(from, to) ==
  /\ state = from
  /\ state' = to

Init == state = "BothOff"

Next ==
  \/ Trans("BothOff", "WallOff")
  \/ Trans("BothOff", "LampOff")
  \/ Trans("WallOff", "On")
  \/ Trans("WallOff", "BothOff")
  \/ Trans("LampOff", "On")
  \/ Trans("LampOff", "BothOff")
  \/ Trans("On", "WallOff")
  \/ Trans("On", "LampOff")

Spec == Init /\ [][Next]_state
```

### Hierarchical State Machines

Model parent-child state relationships with a recursive `In` operator:

```tla
TopDown == [LogIn |-> {"Main", "Settings", "Reports"},
            Reports |-> {"Report1", "Report2"}]
           @@ [s \in States |-> {}]

RECURSIVE InTD(_, _)
InTD(s, p) ==
  \/ s = p
  \/ \E c \in TopDown[p] : InTD(s, c)

Trans(from, to) ==
  /\ InTD(state, from)     \* Current state is inside "from"
  /\ state' = to            \* Transition to leaf state "to"
```

### Message Queue (Destructive Read)

```tla
variables queue = <<>>;

\* Write
queue := Append(queue, msg);

\* Read (with guard)
await queue # <<>>;
msg := Head(queue);
queue := Tail(queue);

\* Or with if (non-blocking)
if queue # <<>> then
  msg := Head(queue);
  queue := Tail(queue);
end if;
```

### Message Types (Tagged Unions)

```tla
AlphaMsg == [id: Nat, from: Writer, msg: {"alpha"}, data: AlphaData]
BravoMsg == [id: Nat, from: Writer, msg: {"bravo"}, data: BravoData]
MessageType == AlphaMsg \union BravoMsg
```

### Multiple Reader / Writer Queues

```tla
variables queues = [r \in Reader |-> <<>>];

\* Broadcast to all readers
queues := [r \in Reader |-> Append(queues[r], msg)];

\* Nondeterministic read from any writer's queue
with w \in Writer do
  await queues[w] # <<>>;
  msg := Head(queues[w]);
  queues[w] := Tail(queues[w]);
end with;
```

### Lock / Mutual Exclusion

```tla
CONSTANT NULL
variables lock = NULL;

\* Acquire
GetLock:
  await lock = NULL;
  lock := self;

\* Release (with safety check)
ReleaseLock:
  assert lock = self;
  lock := NULL;
```

### Nondeterminism for Abstraction

Model failure/success without specifying implementation details:

```tla
\* Abstract failure: either succeed or fail (skip)
macro request_resource(r) begin
  either
    reserved := reserved \union {r};
  or
    skip;   \* Request failed (any reason)
  end either;
end macro;

\* With error categories
macro request_resource(r) begin
  either
    reserved := reserved \union {r};
    failure_reason := "";
  or
    with reason \in {"unauthorized", "in_use", "timeout"} do
      failure_reason := reason;
    end with;
  end either;
end macro;
```

`either action or skip end either` is the standard "might fail" idiom.

### Finding Solutions via Invariant Negation

Use TLC as a constraint solver by asserting the NEGATION of what you want to find:

```tla
define
  \* "sum can never be 417" -- if it CAN be, TLC shows the path
  Invariant == sum # Target
end define;
```

If TLC finds a violation, the error trace shows a step-by-step path to the target state.

### State Sweeping

Use initial-state nondeterminism to parameterize variables:

```tla
variable
  n \in 1..MaxSize;         \* Try all lengths
  seq \in [1..n -> S];      \* Sequence of that length
```

TLC checks sequences of ALL lengths from 1 to MaxSize.

### Minimum Selection from a Set (Event Queue)

When using a set as a priority queue, use `\E` (not `CHOOSE`) for min-element selection so TLC explores all tie-breaking orderings:

```tla
\* CORRECT: \E explores all min-time events nondeterministically
ProcessNext ==
    \E event \in queue :
        /\ \A other \in queue : event.time <= other.time
        /\ now' = event.time
        /\ queue' = queue \ {event}
        /\ processed' = processed \union {event}
        /\ UNCHANGED <<nextId>>
```

`CHOOSE` picks one arbitrary but fixed element -- TLC will not explore other orderings. `\E` produces a separate successor state for each minimum element, which is what you want.

**Unique IDs are required for set-based queues.** Sets collapse duplicate elements, so two events with the same `(time, type)` pair silently merge into one. Always include a unique ID field:

```tla
Event == [id: 1..MaxEvents, time: 0..MaxTime, type: EventTypes]
```

For deterministic tie-breaking, add an explicit comparator: `Before(e1, e2) == e1.time < e2.time \/ (e1.time = e2.time /\ e1.id < e2.id)`.

---

## Dangerous Patterns

### Conjunction Scoping with Quantifiers

In TLA+, indentation determines how `/\` conjuncts group. When mixing `\A`/`\E` quantifiers with primed state updates, misaligned conjuncts silently change semantics:

```tla
\* WRONG: primed updates are inside the \A body due to indentation
ProcessNext ==
    \E event \in queue :
        /\ \A other \in queue : event.time <= other.time
            /\ now' = event.time          \* Nested under \A!
            /\ queue' = queue \ {event}   \* Nested under \A!

\* CORRECT: \A is one conjunct; primed updates are sibling conjuncts at the \E level
ProcessNext ==
    \E event \in queue :
        /\ \A other \in queue : event.time <= other.time
        /\ now' = event.time
        /\ queue' = queue \ {event}
```

In the wrong version, `now' = event.time` is conjoined with the universally quantified comparison, making the entire `\A` expression semantically nonsensical. TLC may reject or silently misinterpret it. Always ensure primed state updates align with the outer `\E` quantifier, not with any inner `\A`.

### Explicit Stutter as a Named Action

Do not add `Stutter == UNCHANGED vars` as a disjunct in `Next`:

```tla
\* WRONG: defeats deadlock detection
Next ==
    \/ RealAction
    \/ Stutter   \* System can always do nothing -- TLC can never find deadlocks

\* CORRECT: stuttering is already allowed by the subscript notation
Spec == Init /\ [][Next]_vars   \* This already permits stuttering
```

The `[][Next]_vars` form means "either `Next` holds or all variables are unchanged." An explicit stutter action makes `Next` always enabled, so TLC's `CHECK_DEADLOCK` becomes useless.

---

## Complete Examples

### Wire Transfer (Race Condition Detection)

```tla
---- MODULE wire ----
EXTENDS TLC, Integers
CONSTANTS People, Money, NumTransfers

ASSUME People # {}
ASSUME Money \subseteq Nat
ASSUME NumTransfers \in Nat

(* --algorithm wire
variables
  acct \in [People -> Money];

define
  NoOverdrafts == \A p \in People : acct[p] >= 0
end define;

process wire \in 1..NumTransfers
variable
  amnt \in 1..5;
  from \in People;
  to \in People;
begin
  Check:
    if acct[from] >= amnt then
      Withdraw:
        acct[from] := acct[from] - amnt;
      Deposit:
        acct[to] := acct[to] + amnt;
    end if;
end process;
end algorithm; *)
====
```

Model config: `People <- {alice, bob}`, `Money <- 1..10`, `NumTransfers <- 2`, `INVARIANT NoOverdrafts`. This FAILS -- the error trace shows a race condition where two transfers pass the guard before either withdraws.

### Threads with Lock (Mutual Exclusion)

```tla
---- MODULE threads ----
EXTENDS TLC, Sequences, Integers
CONSTANT NULL

NumThreads == 2
Threads == 1..NumThreads

(* --algorithm threads

variables
  counter = 0;
  lock = NULL;

define
  AllDone == \A t \in Threads : pc[t] = "Done"
  Correct == AllDone => counter = NumThreads
  Liveness == <>[](counter = NumThreads)
end define;

fair process thread \in Threads
variables tmp = 0;
begin
  GetLock:
    await lock = NULL;
    lock := self;
  GetCounter:
    tmp := counter;
  IncCounter:
    counter := tmp + 1;
  ReleaseLock:
    assert lock = self;
    lock := NULL;
end process;
end algorithm; *)

\* Below translation: type invariant referencing local var
TypeInvariant ==
  /\ counter \in 0..NumThreads
  /\ tmp \in [Threads -> 0..NumThreads]
  /\ lock \in Threads \union {NULL}
====
```

Model config: `NULL <- [model value]`, `INVARIANT Correct`, `PROPERTY Liveness`.

### Producer-Consumer with Bounded Queue

```tla
---- MODULE prodcons ----
EXTENDS Integers, Sequences, TLC
CONSTANTS Producers, Consumers, BufSize, NULL, MaxMsg

ASSUME Producers # {}
ASSUME Consumers # {}
ASSUME Producers \intersect Consumers = {}
ASSUME BufSize > 0
ASSUME MaxMsg > 0

(* --algorithm prodcons
variables
  buf = <<>>;
  produced = [p \in Producers |-> 0];
  consumed = [c \in Consumers |-> 0];

define
  TypeInvariant ==
    /\ buf \in Seq(Producers)
    /\ Len(buf) <= BufSize
    /\ produced \in [Producers -> 0..MaxMsg]
    /\ consumed \in [Consumers -> Nat]

  \* No message is lost
  RECURSIVE SumFunc(_, _)
  SumFunc(f, S) == IF S = {} THEN 0
                   ELSE LET x == CHOOSE x \in S : TRUE
                        IN f[x] + SumFunc(f, S \ {x})

  TotalProduced == SumFunc(produced, Producers)
  TotalConsumed == SumFunc(consumed, Consumers)

  Safety == TotalConsumed <= TotalProduced
end define;

fair process producer \in Producers
variable count = 0;
begin
  Produce:
    while count < MaxMsg do
      await Len(buf) < BufSize;
      buf := Append(buf, self);
      produced[self] := produced[self] + 1;
      count := count + 1;
    end while;
end process;

fair process consumer \in Consumers
begin
  Consume:
    while TRUE do
      await buf # <<>>;
      buf := Tail(buf);
      consumed[self] := consumed[self] + 1;
    end while;
end process;
end algorithm; *)
====
```

Model config: `Producers <- {p1, p2}`, `Consumers <- {c1}`, `BufSize <- 2`, `MaxMsg <- 3`, `NULL <- [model value]`, `INVARIANT TypeInvariant`, `INVARIANT Safety`. Disable deadlock checking (consumers loop forever).

### Order Workflow (Lifecycle State Machine)

```tla
--------------------------- MODULE OrderWorkflow ---------------------------
\* Order Workflow Specification
\* Models the lifecycle of an order from creation to completion

EXTENDS Integers, Sequences, FiniteSets, TLC

CONSTANTS
    MaxOrders,      \* Maximum number of concurrent orders
    MaxItems,       \* Maximum items per order
    Customers,      \* Set of customer IDs
    Products,       \* Set of product IDs
    NULL            \* Sentinel value (model value in config)

VARIABLES
    orders,         \* Function from OrderId -> Order state
    inventory,      \* Function from ProductId -> quantity
    payments,       \* Set of processed payment records
    notifications   \* Sequence of sent notifications

vars == <<orders, inventory, payments, notifications>>

ASSUME Customers # {}
ASSUME Products # {}
ASSUME MaxOrders \in Nat /\ MaxOrders > 0
ASSUME MaxItems \in Nat /\ MaxItems > 0

-----------------------------------------------------------------------------
\* Type Definitions
-----------------------------------------------------------------------------

OrderStatus == {"Draft", "Submitted", "Paid", "Shipped", "Delivered", "Cancelled"}

NotificationType == {"OrderSubmitted", "PaymentReceived",
                     "OrderShipped", "OrderDelivered", "OrderCancelled"}

\* Recursive helper to sum a set of integers
RECURSIVE SetSum(_)
SetSum(S) ==
    IF S = {} THEN 0
    ELSE LET x == CHOOSE x \in S : TRUE
         IN x + SetSum(S \ {x})

\* Collect quantities for a given product from an items set
\* items is a finite set of <<product, quantity>> pairs
QuantityForProduct(items, p) ==
    LET matching == {qty \in {q : <<pr, q>> \in items} : <<p, qty>> \in items}
    IN SetSum(matching)

TypeInvariant ==
    /\ \A o \in DOMAIN orders :
        /\ orders[o].id \in Nat
        /\ orders[o].customerId \in Customers
        /\ orders[o].status \in OrderStatus
        /\ orders[o].total \in Nat
    /\ inventory \in [Products -> Nat]
    /\ payments \in SUBSET [orderId: Nat, amount: Nat]
    /\ notifications \in Seq([type: NotificationType, orderId: Nat])

-----------------------------------------------------------------------------
\* Initial State
-----------------------------------------------------------------------------

Init ==
    /\ orders = [o \in {} |-> NULL]
    /\ inventory = [p \in Products |-> 100]  \* Start with 100 of each
    /\ payments = {}
    /\ notifications = <<>>

-----------------------------------------------------------------------------
\* Actions
-----------------------------------------------------------------------------

\* Create a new draft order
CreateOrder(customerId, orderId) ==
    /\ orderId \notin DOMAIN orders
    /\ Cardinality(DOMAIN orders) < MaxOrders
    /\ orders' = orders @@ (orderId :> [
           id |-> orderId,
           customerId |-> customerId,
           items |-> {},
           status |-> "Draft",
           total |-> 0
       ])
    /\ UNCHANGED <<inventory, payments, notifications>>

\* Add item to draft order
AddItem(orderId, productId, quantity) ==
    /\ orderId \in DOMAIN orders
    /\ orders[orderId].status = "Draft"
    /\ quantity > 0
    /\ quantity <= inventory[productId]
    /\ Cardinality(orders[orderId].items) < MaxItems
    /\ orders' = [orders EXCEPT
           ![orderId].items = @ \cup {<<productId, quantity>>},
           ![orderId].total = @ + (quantity * 10)]  \* Simplified pricing
    /\ UNCHANGED <<inventory, payments, notifications>>

\* Submit order for processing -- reserves inventory
SubmitOrder(orderId) ==
    /\ orderId \in DOMAIN orders
    /\ orders[orderId].status = "Draft"
    /\ orders[orderId].items /= {}
    /\ \A <<p, q>> \in orders[orderId].items : inventory[p] >= q
    /\ orders' = [orders EXCEPT ![orderId].status = "Submitted"]
    /\ inventory' = [p \in Products |->
           inventory[p] - QuantityForProduct(orders[orderId].items, p)]
    /\ notifications' = Append(notifications,
           [type |-> "OrderSubmitted", orderId |-> orderId])
    /\ UNCHANGED <<payments>>

\* Process payment
ProcessPayment(orderId, amount) ==
    /\ orderId \in DOMAIN orders
    /\ orders[orderId].status = "Submitted"
    /\ amount = orders[orderId].total
    /\ payments' = payments \cup {[orderId |-> orderId, amount |-> amount]}
    /\ orders' = [orders EXCEPT ![orderId].status = "Paid"]
    /\ notifications' = Append(notifications,
           [type |-> "PaymentReceived", orderId |-> orderId])
    /\ UNCHANGED <<inventory>>

\* Ship order
ShipOrder(orderId) ==
    /\ orderId \in DOMAIN orders
    /\ orders[orderId].status = "Paid"
    /\ orders' = [orders EXCEPT ![orderId].status = "Shipped"]
    /\ notifications' = Append(notifications,
           [type |-> "OrderShipped", orderId |-> orderId])
    /\ UNCHANGED <<inventory, payments>>

\* Deliver order
DeliverOrder(orderId) ==
    /\ orderId \in DOMAIN orders
    /\ orders[orderId].status = "Shipped"
    /\ orders' = [orders EXCEPT ![orderId].status = "Delivered"]
    /\ notifications' = Append(notifications,
           [type |-> "OrderDelivered", orderId |-> orderId])
    /\ UNCHANGED <<inventory, payments>>

\* Cancel order (only draft or submitted)
CancelOrder(orderId) ==
    /\ orderId \in DOMAIN orders
    /\ orders[orderId].status \in {"Draft", "Submitted"}
    /\ orders' = [orders EXCEPT ![orderId].status = "Cancelled"]
    \* Return inventory if was submitted
    /\ inventory' = IF orders[orderId].status = "Submitted"
                    THEN [p \in Products |->
                          inventory[p] + QuantityForProduct(orders[orderId].items, p)]
                    ELSE inventory
    /\ notifications' = Append(notifications,
           [type |-> "OrderCancelled", orderId |-> orderId])
    /\ UNCHANGED <<payments>>

-----------------------------------------------------------------------------
\* Next State Relation
-----------------------------------------------------------------------------

Next ==
    \/ \E c \in Customers, o \in 1..MaxOrders : CreateOrder(c, o)
    \/ \E o \in DOMAIN orders, p \in Products, q \in 1..5 : AddItem(o, p, q)
    \/ \E o \in DOMAIN orders : SubmitOrder(o)
    \/ \E o \in DOMAIN orders : ProcessPayment(o, orders[o].total)
    \/ \E o \in DOMAIN orders : ShipOrder(o)
    \/ \E o \in DOMAIN orders : DeliverOrder(o)
    \/ \E o \in DOMAIN orders : CancelOrder(o)

Spec == Init /\ [][Next]_vars

-----------------------------------------------------------------------------
\* Safety Properties
-----------------------------------------------------------------------------

\* No negative inventory
InventoryNonNegative ==
    \A p \in Products : inventory[p] >= 0

\* Order status transitions are valid
ValidStatusTransitions ==
    \A o \in DOMAIN orders :
        orders[o].status \in OrderStatus

\* Payment only for orders that exist
PaymentOnlyForOrders ==
    \A p \in payments :
        p.orderId \in DOMAIN orders

\* No double payments
NoDoublePayment ==
    \A p1, p2 \in payments :
        p1.orderId = p2.orderId => p1 = p2

-----------------------------------------------------------------------------
\* Liveness Properties (requires fairness)
-----------------------------------------------------------------------------

\* Every submitted order eventually completes (delivered or cancelled)
EventualCompletion ==
    \A o \in DOMAIN orders :
        orders[o].status = "Submitted" ~>
            orders[o].status \in {"Delivered", "Cancelled"}

\* If payment succeeds, order eventually ships
PaymentLeadsToShipment ==
    \A o \in DOMAIN orders :
        orders[o].status = "Paid" ~> orders[o].status = "Shipped"

=============================================================================
```

Model config: `Customers <- {c1, c2}`, `Products <- {p1, p2}`, `MaxOrders <- 2`, `MaxItems <- 2`, `NULL <- [model value]`, `INVARIANT TypeInvariant`, `INVARIANT InventoryNonNegative`, `INVARIANT NoDoublePayment`.

### Consensus Algorithm

```tla
--------------------------- MODULE SimpleConsensus ---------------------------
EXTENDS Integers, FiniteSets

CONSTANTS
    Nodes,      \* Set of participant nodes
    Values,     \* Possible values to agree on
    Quorum,     \* Minimum nodes for quorum
    NULL        \* Sentinel model value

ASSUME Nodes # {}
ASSUME Values # {}
ASSUME Quorum \in Nat /\ Quorum > 0
ASSUME Quorum <= Cardinality(Nodes)

VARIABLES
    proposed,   \* proposed[n] = value proposed by node n
    accepted,   \* accepted[n] = value accepted by node n
    decided     \* decided[n] = final decided value (or NULL)

vars == <<proposed, accepted, decided>>

TypeOK ==
    /\ proposed \in [Nodes -> Values \cup {NULL}]
    /\ accepted \in [Nodes -> Values \cup {NULL}]
    /\ decided \in [Nodes -> Values \cup {NULL}]

Init ==
    /\ proposed = [n \in Nodes |-> NULL]
    /\ accepted = [n \in Nodes |-> NULL]
    /\ decided = [n \in Nodes |-> NULL]

\* Node proposes a value
Propose(n, v) ==
    /\ proposed[n] = NULL
    /\ proposed' = [proposed EXCEPT ![n] = v]
    /\ UNCHANGED <<accepted, decided>>

\* Node accepts a proposed value
Accept(n, v) ==
    /\ \E m \in Nodes : proposed[m] = v
    /\ accepted[n] = NULL
    /\ accepted' = [accepted EXCEPT ![n] = v]
    /\ UNCHANGED <<proposed, decided>>

\* Node decides if quorum reached
Decide(n) ==
    /\ decided[n] = NULL
    /\ \E v \in Values :
        /\ Cardinality({m \in Nodes : accepted[m] = v}) >= Quorum
        /\ decided' = [decided EXCEPT ![n] = v]
    /\ UNCHANGED <<proposed, accepted>>

Next ==
    \/ \E n \in Nodes, v \in Values : Propose(n, v)
    \/ \E n \in Nodes, v \in Values : Accept(n, v)
    \/ \E n \in Nodes : Decide(n)

Spec == Init /\ [][Next]_vars

\* Safety: Agreement -- all decided values are the same
Agreement ==
    \A n1, n2 \in Nodes :
        decided[n1] /= NULL /\ decided[n2] /= NULL =>
            decided[n1] = decided[n2]

\* Safety: Validity -- decided value was proposed
Validity ==
    \A n \in Nodes :
        decided[n] /= NULL =>
            \E m \in Nodes : proposed[m] = decided[n]

=============================================================================
```

### Two-Phase Commit

```tla
--------------------------- MODULE TwoPhaseCommit ---------------------------
EXTENDS Integers, FiniteSets

CONSTANTS
    Participants

ASSUME Participants # {}

VARIABLES
    coordState,     \* Coordinator state
    partState,      \* Participant states
    prepared,       \* Set of prepared participants
    decision        \* Final decision

vars == <<coordState, partState, prepared, decision>>

CoordStates == {"init", "waiting", "committed", "aborted"}
PartStates == {"working", "prepared", "committed", "aborted"}

TypeOK ==
    /\ coordState \in CoordStates
    /\ partState \in [Participants -> PartStates]
    /\ prepared \in SUBSET Participants
    /\ decision \in {"pending", "commit", "abort"}

Init ==
    /\ coordState = "init"
    /\ partState = [p \in Participants |-> "working"]
    /\ prepared = {}
    /\ decision = "pending"

\* Coordinator sends prepare request
SendPrepare ==
    /\ coordState = "init"
    /\ coordState' = "waiting"
    /\ UNCHANGED <<partState, prepared, decision>>

\* Participant prepares (votes yes)
Prepare(p) ==
    /\ partState[p] = "working"
    /\ partState' = [partState EXCEPT ![p] = "prepared"]
    /\ prepared' = prepared \cup {p}
    /\ UNCHANGED <<coordState, decision>>

\* Participant aborts (votes no)
Abort(p) ==
    /\ partState[p] = "working"
    /\ partState' = [partState EXCEPT ![p] = "aborted"]
    /\ UNCHANGED <<coordState, prepared, decision>>

\* Coordinator decides commit (all prepared)
DecideCommit ==
    /\ coordState = "waiting"
    /\ prepared = Participants
    /\ coordState' = "committed"
    /\ decision' = "commit"
    /\ partState' = [p \in Participants |-> "committed"]
    /\ UNCHANGED <<prepared>>

\* Coordinator decides abort (any aborted)
DecideAbort ==
    /\ coordState = "waiting"
    /\ \E p \in Participants : partState[p] = "aborted"
    /\ coordState' = "aborted"
    /\ decision' = "abort"
    /\ partState' = [p \in Participants |->
           IF partState[p] = "prepared" THEN "aborted" ELSE partState[p]]
    /\ UNCHANGED <<prepared>>

Next ==
    \/ SendPrepare
    \/ \E p \in Participants : Prepare(p)
    \/ \E p \in Participants : Abort(p)
    \/ DecideCommit
    \/ DecideAbort

Spec == Init /\ [][Next]_vars

\* Safety: Atomicity -- all participants reach same decision
Atomicity ==
    decision /= "pending" =>
        \A p \in Participants :
            (decision = "commit" => partState[p] = "committed") /\
            (decision = "abort" => partState[p] \in {"aborted", "working"})

=============================================================================
```

---

## TLC Model Checking

### Configuration File (.cfg)

```text
SPECIFICATION Spec

\* Constants
CONSTANTS
    Nodes = {n1, n2, n3}
    Values = {v1, v2}
    Quorum = 2
    NULL = NULL

\* Invariants to check
INVARIANT TypeOK
INVARIANT Agreement
INVARIANT Validity

\* Liveness properties
PROPERTY EventuallyDecided

\* Constraints for bounded model checking
CONSTRAINT StateConstraint

\* Symmetry for optimization
SYMMETRY Symmetry
```

### Running TLC

```bash
# Command-line TLC
java -jar tla2tools.jar -config Spec.cfg Spec.tla

# With workers for parallelism
java -jar tla2tools.jar -workers 4 -config Spec.cfg Spec.tla

# Generate state graph on error
java -jar tla2tools.jar -dump dot,colorize states.dot Spec.tla
```

### Reading TLC Output

- **"No errors found"** + state count = no counterexample found for those constants. Verify non-vacuity before treating as "pass".
- **"Invariant X is violated"** = TLC found a reachable state that breaks your invariant. It prints the exact trace (sequence of states) leading to the violation. This is the gold.
- **"Deadlock reached"** = system can reach a state where no action is enabled.
- **"Property X is violated"** = liveness property fails (system can get stuck forever).

### Common Fixes

| TLC finding | Typical fix |
|---|---|
| Race condition (two actors modify same state) | Add mutual exclusion (lock, compare-and-swap, serial queue) |
| Lost update (read-then-write not atomic) | Make it atomic or use optimistic locking |
| Deadlock (circular wait) | Impose lock ordering or use try-lock |
| Starvation (liveness violation) | Add fairness constraint or priority mechanism |
| Queue double-processing | Idempotency keys or exactly-once delivery |

### Separate Safety, Liveness, and Vacuity Models

Use three separate cfg files. Liveness checking is significantly more expensive than safety. Vacuity checks confirm the model actually explores interesting behaviors.

**Safety cfg** -- large constants, symmetry sets, all invariants, deadlock checking:

```text
\* Spec_Safety.cfg
SPECIFICATION SafetySpec        \* Init /\ [][Next]_vars (no fairness)
CONSTANTS
    MaxEvents = 5
    MaxTime = 4
    EventTypes = {a, b}
INVARIANT TypeInvariant
INVARIANT SafetyProperty1
INVARIANT SafetyProperty2
SYMMETRY EventTypeSymmetry      \* Safe for invariant checking
```

**Liveness cfg** -- smaller constants (liveness explores the full behavior graph), NO symmetry (incompatible with liveness), fairness via `FairSpec`:

```text
\* Spec_Liveness.cfg
SPECIFICATION FairSpec           \* Init /\ [][Next]_vars /\ WF_vars(Action1) /\ ...
CONSTANTS
    MaxEvents = 3
    MaxTime = 2
    EventTypes = {a, b}
PROPERTY EventualTermination
PROPERTY AllEventsProcessed
CHECK_DEADLOCK FALSE             \* Terminal states are expected
```

**Vacuity cfg** -- same small constants, invariants that are **expected to FAIL**, confirming TLC explores meaningful states:

```text
\* Spec_Vacuity.cfg
\* These invariants should ALL be violated. If any passes, the model is
\* too constrained and the corresponding safety/liveness property may
\* hold vacuously.
SPECIFICATION SafetySpec
CONSTANTS
    MaxEvents = 3
    MaxTime = 2
    EventTypes = {a, b}
INVARIANT VacuityNoProcessing    \* "Nothing is ever processed"  -- must fail
INVARIANT VacuityNeverDone       \* "System never terminates"    -- must fail
INVARIANT VacuityClockStaysZero  \* "Clock never advances"       -- must fail
CHECK_DEADLOCK FALSE
```

Define vacuity invariants as the negation of behaviors you expect the model to exhibit:

```tla
\* These should FAIL -- if they pass, the model is degenerate
VacuityNoProcessing   == processed = {}
VacuityNeverDone      == simState # "done"
VacuityClockStaysZero == clock = 0
```

If a vacuity invariant passes (no violation found), it means TLC never reached a state where that behavior occurs. Any property that depends on that behavior holds vacuously -- it is "true" only because the premise is never satisfied.

---

## Best Practices

### Key Principles

- **One action per atomic step**: If two things can't happen simultaneously in the real system, they're separate actions.
- **Model crashes**: Add a `Crash` action that resets actor state mid-operation.
- **Model network**: Add `NetworkDrop` / `NetworkRestore` actions.
- **Minimize state space**: Use small constants (2-3 workers, 3-5 queue items). TLC checks ALL interleavings, so the state space explodes combinatorially.
- **Name variables from the real system**: Use the same names found in other design docs and code for traceability.

### General Tips

- Every `CONSTANT` should have an `ASSUME` documenting valid values.
- Keep `TypeInvariant` for bounds only; separate correctness properties into their own invariants.
- Decompose `[Worker -> WorkerState]` structs into separate variables (`worker_queue`, `worker_online`) to avoid single-update-per-label issues in PlusCal.
- Use `THEOREM Spec => []TypeInvariant` to document intended properties (not checked by TLC, but useful documentation).
- Group variables: `vars == <<v1, v2, v3>>` for cleaner `UNCHANGED vars`.
- Parameterize actions and push `\E` to the outermost level:
  ```tla
  \* Better: reuse w across actions
  Next == \E w \in Worker :
    \/ Add(w)
    \/ Remove(w)
  ```
- Use `@` in EXCEPT for cleaner updates: `[f EXCEPT ![k] = @ + 1]`
- Tagged unions for mixed-type sets: `[type |-> "int", val |-> 1]`

---

## References

For detailed TLA+ and PlusCal language reference, load [references/tlaplus-reference.md](./references/tlaplus-reference.md).

General documentation is found at [learntla.com](https://learntla.com/).
