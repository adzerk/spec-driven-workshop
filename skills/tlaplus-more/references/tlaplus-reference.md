# TLA+ and PlusCal Language Reference

Complete syntax and semantics reference for TLA+ specifications and PlusCal algorithms.

---

## 1. Module Structure

Every TLA+ file is a module. The module name MUST match the filename (without `.tla`).

```tla
---- MODULE modulename ----
EXTENDS Integers, Sequences, TLC, FiniteSets
CONSTANTS Workers, NULL, MaxNum
ASSUME Workers # {}
ASSUME NULL \notin Workers

VARIABLES counter, lock, queue

(* --algorithm alg_name
  \* PlusCal goes here inside a comment block
end algorithm; *)

\* BEGIN TRANSLATION
\* ... auto-generated TLA+ from PlusCal ...
\* END TRANSLATION

\* Operators that reference process-local variables go below the translation

====
```

Key rules:
- Only ONE `EXTENDS` line allowed; comma-separate multiple modules.
- `CONSTANTS` are parameterized per model run; `VARIABLES` are mutable state.
- Everything above `----` and below `====` is ignored (use for documentation/scratch).
- PlusCal lives inside `(* --algorithm name ... end algorithm; *)` comment block.
- The algorithm name does not need to match the module name.

---

## 2. Operators

Operators are the TLA+ equivalent of functions. They are defined with `==` (double equals).

```tla
\* Zero-argument operator (effectively a constant)
SecondsPerMinute == 60

\* Parameterized operator
MinutesToSeconds(m) == m * 60

\* Conditional expression (ELSE is mandatory)
Abs(x) == IF x < 0 THEN -x ELSE x

\* LET for local sub-operators
ToClock(seconds) ==
  LET
    h == seconds \div 3600
    remainder == seconds % 3600
    m == remainder \div 60
    s == remainder % 60
  IN <<h, m, s>>

\* CASE expression
Fizzbuzz(x) ==
  CASE (x % 3 = 0) /\ (x % 5 = 0) -> "Fizzbuzz"
    [] (x % 3 = 0)                  -> "Fizz"
    [] (x % 5 = 0)                  -> "Buzz"
    [] OTHER                         -> x
```

CRITICAL syntax rules:
- `==` for DEFINITIONS. `=` for COMPARISON. `:=` for PlusCal UPDATES.
- Never use `=` to define an operator or `==` to compare values.
- No default arguments, overloading, or optional parameters.

---

## 3. Primitive Values

| Type | Examples | Notes |
|------|----------|-------|
| Integer | `0`, `-5`, `42` | Requires `EXTENDS Integers`. No floats. |
| String | `"hello"` | Double quotes only. No operations except `=` and `#`. Used as opaque identifiers. |
| Boolean | `TRUE`, `FALSE` | The set of all booleans is `BOOLEAN`. |
| Model Value | `NULL` | Declared as `CONSTANT`, assigned `[model value]` in model config. Only supports `=` and `#`. |

---

## 4. Boolean Logic

| Logic | TLA+ | Mnemonic |
|-------|------|----------|
| and | `/\` | Looks like "A" for And |
| or | `\/` | The other one |
| not | `~` | Flippy thing |
| implies | `=>` | If P then Q (false only when P=TRUE, Q=FALSE) |
| iff | `<=>` | Same as `A = B` for booleans |
| not equals | `#` | |

Bullet-point notation (ONLY place where whitespace matters):

```tla
\* Equivalent to: A /\ (B \/ C) /\ (D \/ (E /\ F))
/\ A
/\ \/ B
   \/ C
/\ \/ D
   \/ /\ E
      /\ F
```

WARNING: Indentation determines grouping. A misaligned `/\` or `\/` changes the meaning entirely.

---

## 5. Sets

Sets are unordered collections of unique values. They cannot mix types.

```tla
\* Literal set
S == {1, 2, 3}

\* Integer range (requires EXTENDS Integers)
OneToTen == 1..10       \* {1, 2, 3, ..., 10}
Empty == 5..3           \* {} (empty when a > b)

\* Set of all booleans
BOOLEAN                 \* {TRUE, FALSE}
```

### Set Operators

| Expression | Meaning |
|------------|---------|
| `x \in S` | x is an element of S |
| `x \notin S` | x is not an element of S |
| `S \subseteq T` | S is a subset of (or equal to) T |
| `S \union T` | Set union |
| `S \intersect T` | Set intersection |
| `S \ T` | Set difference (elements in S but not T) |
| `SUBSET S` | Power set (set of ALL subsets of S). Careful: size is 2^&#124;S&#124;. |
| `Cardinality(S)` | Number of elements (requires `EXTENDS FiniteSets`) |
| `S = {}` | Test if empty |

### Set Constructors (Map and Filter)

```tla
\* Map: "x squared WHERE x in 1..4"
Squares == {x*x : x \in 1..4}              \* {1, 4, 9, 16}

\* Filter: "x in 1..4 WHERE x is even"
Evens == {x \in 1..4 : x % 2 = 0}          \* {2, 4}

\* Cartesian product (set of tuples)
Pairs == {1, 2} \X {"a", "b"}              \* {<<1,"a">>, <<1,"b">>, <<2,"a">>, <<2,"b">>}

\* Range of a sequence (set of all elements)
Range(seq) == {seq[i] : i \in 1..Len(seq)}
```

### CHOOSE

Deterministic selection of one element from a set satisfying a predicate.

```tla
\* Pick the element in ClockType whose seconds match
ToClock(seconds) == CHOOSE x \in ClockType : ToSeconds(x) = seconds

\* Pick any element (TLC always picks the lowest)
Min(S) == CHOOSE x \in S : \A y \in S : x <= y
```

- If NO element satisfies the predicate, TLC raises an error.
- If MULTIPLE elements satisfy it, the result is deterministic (TLC picks the smallest).

---

## 6. Sequences

Sequences are ordered lists, written `<<a, b, c>>`. They are **1-indexed**.

```tla
seq == <<10, 20, 30>>
seq[1]                  \* 10 (NOT seq[0])
seq[3]                  \* 30
```

Requires `EXTENDS Sequences` for operators:

| Expression | Result | Notes |
|------------|--------|-------|
| `Len(seq)` | `3` | Length |
| `Head(seq)` | `10` | First element |
| `Tail(seq)` | `<<20, 30>>` | All but first |
| `Append(seq, 40)` | `<<10,20,30,40>>` | Append to end |
| `seq \o <<40, 50>>` | `<<10,20,30,40,50>>` | Concatenation |
| `SubSeq(seq, 1, 2)` | `<<10, 20>>` | Subsequence (inclusive) |
| `SelectSeq(seq, Test)` | filtered seq | Filter with operator |
| `Seq(S)` | infinite set | Set of all sequences over S (membership only, not enumerable) |

Sequences are 1-indexed. `DOMAIN seq = 1..Len(seq)`.

---

## 7. Functions

Functions map a domain set to values. Sequences and structures are both special cases of functions.

```tla
\* Function definition: [x \in DomainSet |-> expression]
Double == [x \in 1..10 |-> x * 2]
Double[3]              \* 6

\* Multi-argument functions
Prod == [x \in 1..5, y \in 1..5 |-> x * y]
Prod[<<3, 4>>]         \* 12

\* DOMAIN returns the domain set
DOMAIN Double           \* 1..10
DOMAIN <<"a","b","c">>  \* 1..3 (sequences are functions)
```

### Function Sets

`[S -> T]` is the set of ALL functions mapping S to T. Size: |T|^|S|.

```tla
\* All possible server statuses
[Server -> {"online", "offline", "booting"}]

\* All binary strings of length n
[1..n -> BOOLEAN]

\* All possible allocations (with unallocated option)
[Resource -> User \union {NULL}]
```

### Function Update with EXCEPT

Since TLA+ requires the full next-state value, use EXCEPT to create a modified copy:

```tla
\* Update one key
f' = [f EXCEPT ![key] = newval]

\* Update multiple keys
f' = [f EXCEPT ![1] = "a", ![2] = "b"]

\* Reference old value with @
counter' = [counter EXCEPT ![c] = @ + 1]

\* Nested update
f' = [f EXCEPT ![1].x = ~@]
```

In PlusCal, `:=` on function elements auto-generates EXCEPT. You can also use `@`:
```
counter[i] := @ + 1;
```

### Utility Operators

```tla
\* Requires EXTENDS TLC
1 :> "a"                \* Single-valued function [x \in {1} |-> "a"]
f1 @@ f2                \* Function merge (left-biased: f1's keys take priority)

\* These are just syntactic sugar:
1 :> "a" @@ 2 :> "b"   \* <<"a", "b">> (a sequence)
"a" :> 1 @@ "b" :> 2   \* [a |-> 1, b |-> 2] (a structure)
```

---

## 8. Structures

Structures are functions with string domains. They act like hashmaps/records.

```tla
\* Literal structure
s == [name |-> "alice", age |-> 30]
s["name"]               \* "alice"
s.name                  \* "alice" (dot syntax)

\* Structure type set (set of all possible structures)
PersonType == [name: {"alice", "bob"}, age: 1..120]

\* DOMAIN returns the set of keys
DOMAIN s                \* {"name", "age"}
```

CRITICAL: `|->` in literals, `->` in function sets, `:` in struct type sets.

```tla
[key |-> val]           \* A structure (literal value)
[S -> T]                \* A function set (set of all functions S to T)
[key: Set]              \* A struct type set (set of all structs with key in Set)
```

---

## 9. Quantifiers

```tla
\* For all: true if P holds for EVERY element
\A x \in S : P(x)

\* Exists: true if P holds for AT LEAST ONE element
\E x \in S : P(x)

\* Multiple bindings
\A x \in S, y \in T : P(x, y)
\A i, j \in 1..Len(seq) : i # j => seq[i] # seq[j]
```

Key rules:
- `\A x \in {} : P(x)` is always TRUE (vacuously true).
- `\E x \in {} : P(x)` is always FALSE.
- Use `=>` with `\A` to add preconditions: `\A i, j \in S : i # j => P(i, j)`.
- NEVER use `=>` with `\E` (it's almost always wrong). Use `/\` instead.

```tla
\* WRONG: \E i, j \in S : i # j => seq[i] = seq[j]
\*   (trivially true when i = j, since FALSE => anything is TRUE)

\* CORRECT:
\E i, j \in S : i # j /\ seq[i] = seq[j]
```

---

## 10. Recursive Operators

```tla
RECURSIVE SumSeq(_)
SumSeq(s) == IF s = <<>> THEN 0
             ELSE Head(s) + SumSeq(Tail(s))

\* Recursive on sets using CHOOSE
RECURSIVE SetSum(_)
SetSum(set) == IF set = {} THEN 0
  ELSE LET x == CHOOSE x \in set : TRUE
       IN x + SetSum(set \ {x})

\* Inside LET
Op(s) == LET
  RECURSIVE Helper(_)
  Helper(s_) == IF s_ = <<>> THEN 0 ELSE Head(s_) + Helper(Tail(s_))
  IN Helper(s)

\* Recursive function syntax
Factorial[x \in 0..10] == IF x = 0 THEN 1 ELSE x * Factorial[x - 1]
```

### Higher-Order Operators

```tla
\* Operator that takes an operator argument
SeqMap(Op(_), seq) == [i \in DOMAIN seq |-> Op(seq[i])]

\* Anonymous operators with LAMBDA
SeqMap(LAMBDA x : x + 1, <<1, 2, 3>>)    \* <<2, 3, 4>>
```

WARNING: You cannot combine recursive and higher-order operators.

---

## 11. Constants, Model Values, and ASSUME

```tla
CONSTANT Workers, NULL, MaxNum

\* ASSUME validates constants before model checking starts
ASSUME Workers # {}
ASSUME NULL \notin Workers
ASSUME MaxNum \in Nat
ASSUME MaxNum > 0
```

### Model Values

Model values have no operations except `=` and `#`. They are invaluable as sentinel values.

```tla
\* In model config:
\*   NULL <- [model value]
\*   Workers <- [model value] {w1, w2, w3}

\* Usage: safe nullable values
VARIABLE last_access
\* ...
IF last_access = NULL THEN ...   \* Safe: comparing model value to anything is OK
```

Comparing incompatible types (e.g., string to integer) throws a TLC error. Model values avoid this because they compare safely with any type.

### Symmetry Sets

Sets of model values can be made into symmetry sets, reducing state space by roughly n!. Cannot be used with liveness properties.

```tla
\* In model config: Workers <- [model value] [symmetry] {w1, w2, w3}
\* Or in spec for CLI usage:
Symmetry == Permutations(Workers)  \* Requires EXTENDS TLC
\* Then add SYMMETRY Symmetry to config file
```

---

# PlusCal Reference

## 12. PlusCal Algorithm Structure

```tla
(* --algorithm name
variables
  x = 5;                    \* fixed initial value
  y \in 1..10;              \* nondeterministic: TLC tries ALL values
  z = <<>>;

define
  \* Pure TLA+ operators that can reference PlusCal variables
  TypeInvariant ==
    /\ x \in Int
    /\ y \in 1..10
  IsCorrect == pc = "Done" => some_property
end define;

macro inc(var) begin
  var := var + 1;
end macro;

procedure MyProc(arg1)
variables local1 = 0;
begin
  ProcLabel:
    local1 := arg1;
    return;
end procedure;

begin
  Start:
    \* algorithm body
end algorithm; *)
```

Order: `variables` -> `define` -> `macro` -> `procedure` -> `begin`

---

## 13. Labels

Labels define atomic steps. Everything in one label happens instantaneously.

```tla
begin
  A:                         \* Label A: one atomic step
    x := x + 1;
  B:                         \* Label B: a separate step (time passes between A and B)
    y := x * 2;
```

### Label Rules

1. All algorithms MUST begin with a label.
2. `while` loops MUST be preceded by a label.
3. Each variable can only be updated ONCE per label. Use `||` for simultaneous assignment:
   ```tla
   Label:
     seq[1] := seq[1] + 1 ||
     seq[2] := seq[2] - 1;
   ```
4. Macros and `with` blocks CANNOT contain labels.
5. A `goto` MUST be followed by a label.
6. If any branch of an `if` contains a label, the end of the `if` block must be followed by a label.

---

## 14. PlusCal Statements

```tla
\* Assignment (update existing variable)
x := x + 1;

\* Skip (no-op)
skip;

\* Assert (requires EXTENDS TLC; stops model checking if false)
assert x > 0;

\* Goto
goto SomeLabel;

\* If/elsif/else
if x > 0 then
  y := 1;
elsif x = 0 then
  y := 0;
else
  y := -1;
end if;

\* While (NONATOMIC: each iteration is a separate step)
Loop:
  while i <= Len(seq) do
    sum := sum + seq[i];
    i := i + 1;
  end while;
```

### With (Temporary Bindings)

```tla
\* Deterministic binding (uses =)
with tmp_x = x, tmp_y = y do
  y := tmp_x;
  x := tmp_y;
end with;

\* Nondeterministic binding (uses \in): TLC tries ALL values
with roll \in 1..6 do
  sum := sum + roll;
end with;

\* Nondeterministic from a variable set (blocks if set is empty)
with thread \in sleeping do
  sleeping := sleeping \ {thread};
end with;
```

### Either/Or (Nondeterministic Branching)

```tla
either
  action_a();
or
  action_b();
or
  action_c();
end either;
```

TLC explores ALL branches. Can contain labels inside branches.

### Macros

Simple rewrite rules. No labels allowed. Placed above `begin`.

```tla
macro send(queue, msg) begin
  queue := Append(queue, msg);
end macro;
```

Macros perform textual substitution. They CAN use `self` when called from a process set.

### Procedures

Like macros but CAN contain labels. Require `EXTENDS Sequences`.

```tla
procedure Name(arg1, arg2)
variables local1 = 0;
begin
  Step1:
    local1 := arg1 + arg2;
  Step2:
    return;      \* MUST reach return or TLC errors
end procedure;

\* Calling (must be followed by a label or goto)
process p = "p"
begin
  A:
    call Name(1, 2);
  B:
    skip;
end process;
```

---

## 15. Processes and Concurrency

```tla
\* Single process (assigned a fixed value)
process writer = 1
begin
  Write:
    queue := Append(queue, 1);
end process;

\* Process set (one process per element; all must have comparable types)
process worker \in Workers
begin
  Work:
    queue := Append(queue, self);   \* self = the process's value
end process;
```

Rules:
- All processes must have comparable types (all integers, all strings, or all model values).
- Different processes CANNOT share label names.
- `self` returns the process's value. Available in both single processes and process sets.
- `pc` tracks labels: `pc[process_value]` is the current label string. `"Done"` when finished.

### Local Variables

```tla
process worker \in Workers
variables
  tmp = 0;           \* Each process gets its own copy
  task \in Tasks;    \* Each process can start with a different value
begin
  ...
end process;
```

Local variables CANNOT be referenced in `define` blocks or other processes. Use them for bookkeeping (loop counters, temporary storage).

### await (Guards)

```tla
GetLock:
  await lock = NULL;      \* This label can ONLY execute when lock is NULL
  lock := self;
```

- If `await` is false, the label is blocked (the process cannot take this step).
- If NO process can make progress (all blocked or done), TLC reports a **deadlock**.
- Deadlock on termination: When all processes reach "Done" it is NOT a deadlock (PlusCal inserts a `Terminating` action). If a forever-looping process blocks, it IS a deadlock.
- Disable deadlock checking in the model if deadlock is expected behavior.

### Fairness

```tla
\* Weak fairness: process cannot "stop forever" (no infinite stuttering)
fair process worker \in Workers

\* Strong fairness: if a process can ALWAYS INTERMITTENTLY make progress, it will
fair+ process worker \in Workers

\* Strong fairness on individual labels
GetLock:+
  await lock = NULL;
  lock := self;
```

- Without fairness, any process can crash (stutter infinitely) at any time.
- Weak fairness: if action is CONTINUOUSLY enabled, it eventually happens.
- Strong fairness: if action is REPEATEDLY enabled (even intermittently), it eventually happens.
- Not all processes need to be fair. User/environment processes often should NOT be fair.

---

# Properties and Model Checking

## 16. Invariants (Safety Properties)

An invariant must be TRUE in every state of every behavior. Checked under "Invariants" in model config.

```tla
define
  \* Type invariant: bounds-check all variables
  TypeInvariant ==
    /\ counter \in 0..NumThreads
    /\ lock \in Threads \union {NULL}
    /\ queue \in Seq(MessageType)

  \* Correctness invariant
  NoOverdrafts == \A acct \in Accounts : balance[acct] >= 0

  \* Conditional invariant using => (only check at end)
  IsCorrect == pc = "Done" => result = ExpectedResult

  \* Conditional with implication: "if lock is held, holder is a valid thread"
  LockInvariant == lock # NULL => lock \in Threads
end define;
```

When an invariant is violated, TLC produces a step-by-step error trace showing the initial state and each transition leading to the violation.

### The `pc = "Done" =>` Pattern

Use implication to check a property only when the algorithm has finished:

```tla
IsCorrect == pc = "Done" => is_unique = IsUnique(seq)
```

This is TRUE at every intermediate step (because `pc # "Done"` makes `FALSE => anything` = TRUE), and only checks the real property at termination.

For multi-process specs, check that ALL processes are done:

```tla
AllDone == \A t \in Threads : pc[t] = "Done"
Correct == AllDone => counter = NumThreads
```

---

## 17. Temporal Properties

Checked under "Temporal Properties" (NOT "Invariants") in model config.

### [] (always / "box")

`[]P` means P is true in EVERY state of every behavior.

```tla
\* Equivalent to making P an invariant
[]NoOverdrafts

\* "There exists a server that is ALWAYS online" (not an invariant!)
Safety == \E s \in Servers : [](s \in online)
```

### <> (eventually / "diamond")

`<>P` means P is true in AT LEAST ONE state of every behavior. Equivalent to `~[]~P`.

```tla
\* Eventually the counter reaches the target
Liveness == <>(counter = NumThreads)

\* Eventually always (convergence): P becomes true and stays true
StrongLiveness == <>[](counter = NumThreads)

\* Always eventually (recurrence): P is true infinitely often
Recurrence == []<>(time = midnight)
```

### ~> (leads-to)

`P ~> Q` means: whenever P becomes true, Q is eventually true (now or in a future state).

```tla
\* Every inbound task is eventually processed
Liveness ==
  \A t \in TaskType :
    t \in inbound ~> \E w \in Workers : t \in worker_pool[w]
```

`P ~> Q` is triggered EVERY time P becomes true. Even after Q was satisfied, if P becomes true again, Q must become true again.

### Stutter Invariance and Fairness

ALL TLA+ specs allow stutter steps (nothing changes). This models crashes.

```tla
\* This FAILS without fairness (process can stutter forever):
Liveness == <>(counter = NumThreads)

\* Fix: make the process fair
fair process thread \in Threads
```

- Invariants are never broken by stuttering (no state change = no new bad state).
- Liveness properties CAN be broken by stuttering (prevents reaching a good state).
- Solution: use `fair` (weak) or `fair+` (strong) on processes.

### Temporal Property Considerations

- Liveness checking is SIGNIFICANTLY slower than safety checking.
- Cannot use symmetry sets with liveness properties.
- Use SEPARATE models: large constants for safety, small constants for liveness.
- TLC cannot tell you WHICH temporal property was violated (only that one was).
- Error traces for liveness violations may not be minimal.

---

## 18. Action Properties

Properties on TRANSITIONS, not just states. Checked as temporal properties.

```tla
\* counter only increases (or stays the same)
CounterOnlyIncreases == [][counter' >= counter]_counter

\* Lock can't go directly from one thread to another
LockCantBeStolen == [][lock # NULL => lock' = NULL]_lock
```

Syntax: `[][Action]_v` means `[](Action \/ UNCHANGED v)`.

- `x'` is the value of x in the NEXT state.
- `UNCHANGED x` means `x' = x`.
- `UNCHANGED <<x, y, z>>` means all three are unchanged.

### Quantified Action Properties

TLC cannot check `\A x : [][P(x)]_v`. Pull the quantifier INSIDE:

```tla
\* WRONG (TLC error):
\A c \in Counters : [][values[c]' >= values[c]]_values[c]

\* CORRECT (equivalent, and TLC can check it):
[][\A c \in Counters : values[c]' >= values[c]]_values
```

This works because `\A x : []P(x)` is equivalent to `[](\A x : P(x))`.

---

# Pure TLA+ Specifications

## 19. Writing Pure TLA+ (Without PlusCal)

Use pure TLA+ when you need: complex fairness, state machines, refinement, helper actions, or systems that don't map cleanly onto sequential processes.

```tla
---- MODULE example ----
EXTENDS Integers
CONSTANT NULL
VARIABLES counter, lock

vars == <<counter, lock>>

Init ==
  /\ counter = 0
  /\ lock = NULL

\* Actions are boolean operators with primed variables
Acquire(t) ==
  /\ lock = NULL
  /\ lock' = t
  /\ UNCHANGED counter

Release(t) ==
  /\ lock = t
  /\ lock' = NULL
  /\ UNCHANGED counter

Increment(t) ==
  /\ lock = t
  /\ counter' = counter + 1
  /\ UNCHANGED lock

\* Next state relation: disjunction of all possible actions
Next ==
  \E t \in Threads :
    \/ Acquire(t)
    \/ Increment(t)
    \/ Release(t)

\* Spec: Init AND always (Next or stutter)
Spec == Init /\ [][Next]_vars

\* Fairness (optional, for liveness)
Fairness ==
  /\ \A t \in Threads : WF_vars(Acquire(t))
  /\ \A t \in Threads : WF_vars(Release(t))

FairSpec == Spec /\ Fairness
====
```

### Critical Rules for Pure TLA+

1. **Every action MUST fully specify ALL variables.** If a variable doesn't change, write `UNCHANGED var`.
2. `x'` is the value of x in the next state. `x' = 5` means "x is 5 after this step".
3. `[P]_v` means `P \/ UNCHANGED v`. `<<P>>_v` means `P /\ v' # v`.
4. `ENABLED A` is true if action A CAN happen in the current state.
5. `WF_v(A)` (weak fairness): if A is EVENTUALLY ALWAYS enabled, it eventually happens.
6. `SF_v(A)` (strong fairness): if A is ALWAYS EVENTUALLY enabled, it eventually happens.

### Spec == Init /\ [][Next]_vars

This is the canonical form:
- `Init` describes all valid initial states.
- `[][Next]_vars` means: in every step, either `Next` accurately describes the transition, or nothing changes.
- Fairness constraints are appended with `/\`.

---

# Quick References

## 20. Temporal Operators

```text
[]P          - Always P (invariant)
<>P          - Eventually P
P ~> Q       - P leads to Q (if P then eventually Q)
[]<>P        - Infinitely often P
<>[]P        - Eventually always P
P /\ Q       - P and Q
P \/ Q       - P or Q
~P           - Not P
P => Q       - P implies Q
ENABLED A    - Action A is enabled
[A]_v        - A or v unchanged
<<A>>_v      - A and v changes
WF_v(A)      - Weak fairness
SF_v(A)      - Strong fairness
```

---

## 21. Standard Modules

| Module | Key Operators |
|--------|---------------|
| `Integers` | `+`, `-`, `*`, `^`, `%`, `\div`, `..`, `Int`, `Nat`, `-a` (negation) |
| `Naturals` | Same as Integers but no negation, only `Nat` |
| `Sequences` | `Len`, `Head`, `Tail`, `Append`, `\o`, `SubSeq`, `SelectSeq`, `Seq(S)` |
| `FiniteSets` | `Cardinality(S)`, `IsFiniteSet(S)` |
| `TLC` | `Print`, `PrintT`, `Assert`, `:>`, `@@`, `Permutations`, `ToString`, `RandomElement`, `TLCGet`, `TLCSet` |
| `Bags` | `IsABag`, `BagToSet`, `SetToBag`, `EmptyBag`, `(+)`, `(-)`, `BagCardinality`, `CopiesIn`, `SubBag`, `\sqsubseteq` |
| `Json` | `ToJson`, `JsonSerialize`, `JsonDeserialize` |
| `Randomization` | `RandomSubset(k, S)`, `RandomSetOfSubsets(k, n, S)` (WARNING: TLC won't check all states) |

### Useful TLCGet Keys

```tla
TLCGet("level")     \* Current behavior depth (useful for bounding)
TLCGet("distinct")  \* Number of distinct states found
TLCGet("duration")  \* Seconds since model checking started
TLCGet("diameter")  \* Length of longest behavior
```

---

## 22. State Space Estimation

| Structure | Size |
|-----------|------|
| `S` | &#124;S&#124; |
| `SUBSET S` | 2^&#124;S&#124; |
| `S \X T` | &#124;S&#124; * &#124;T&#124; |
| `[S -> T]` | &#124;T&#124;^&#124;S&#124; |
| `[1..n -> S]` | &#124;S&#124;^n |
| `[s: S, t: T]` | &#124;S&#124; * &#124;T&#124; |

These stack: `[A \X B -> SUBSET C]` has `(2^|C|)^(|A|*|B|)` elements.

---

## 23. Optimization Strategies

1. **Use smaller constants** during development. Most bugs appear with small parameters.
2. **Symmetry sets** reduce states by ~n! (model values only, not for liveness).
3. **Separate safety and liveness models.** Liveness is much slower.
4. **Fuse labels** when intermediate concurrency isn't relevant (reduces interleaving).
5. **Remove loader processes** -- replace with nondeterministic initial states.
6. **Reduce nondeterminism** -- use `CHOOSE` when processing order doesn't matter.
7. **Bags over sequences** when ordering doesn't matter.
8. **Construct, don't filter** -- build the exact set you need instead of filtering a huge superset.
9. **Model only what matters** -- prefer `overloaded \in [Server -> BOOLEAN]` over `load \in [Server -> 0..100]`.
10. **Avoid while loops for computation** -- use function reassignment:
    ```tla
    \* BAD: creates a state per iteration
    Double:
      while i <= Len(seq) do
        seq[i] := seq[i] * 2; i := i + 1;
      end while;

    \* GOOD: one atomic step
    Double:
      seq := [i \in 1..Len(seq) |-> seq[i] * 2];
    ```
11. **`TLCGet("level") < N`** as a state constraint for fast iteration during development.

---

## 24. Common Mistakes

| Mistake | Fix |
|---------|-----|
| `Op(x) = expr` (single equals for definition) | Use `Op(x) == expr` |
| `x == y` (double equals for comparison) | Use `x = y` |
| `seq[0]` | Sequences are 1-indexed: `seq[1]` |
| `\E i, j : i # j => P` | Use `/\` not `=>` with `\E`: `\E i, j : i # j /\ P` |
| Two `EXTENDS` lines | One line: `EXTENDS Integers, Sequences, TLC` |
| `CHOOSE x \in S : TRUE` on empty S | Ensure S is non-empty or handle the case |
| Updating a variable twice in one label | Use `\|\|` for simultaneous assignment |
| Missing `UNCHANGED` in pure TLA+ action | Every action must specify ALL variables |
| `[key: "val"]` (string not in set in struct type) | Use `[key: {"val"}]` |
| `[S \|-> T]` in function set | Use `[S -> T]` for sets, `[x \in S \|-> expr]` for literals |
| Misaligned bullet-point `/\` `\/` | Indentation determines grouping; double-check alignment |
| `while` without preceding label | While loops MUST be preceded by a label |
| `goto` not followed by a label | Add a label after every `goto` |
| Unbounded `CHOOSE n : P(n)` (no `\in`) | Always use `CHOOSE x \in S : P(x)` -- unbounded CHOOSE is not checkable by TLC |
