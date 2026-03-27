---
name: alloy-more
description: Write, review, and validate formal specifications of system designs, state machines, protocols, and algorithms using the Alloy 6 modeling language (more details)
license: MIT
metadata:
  domain: formal-methods
  language: alloy
---

# Alloy Formal Modeling Skill

## 1. Purpose and When to Use

Load this skill when the user wants to:

- Model a system design, data structure, state machine, protocol, or algorithm formally
- Review, debug, or extend an existing `.als` specification
- Review, debug, or extend an existing `.md` file that contains `alloy` code blocks
- Check properties such as safety invariants, liveness conditions, or protocol correctness
- Explore design alternatives via bounded model checking

### What is Alloy

Alloy is a formal modeling language. Its core principle is **everything is a relation** -- signatures, fields, and variables are all mathematical relations (sets of tuples). The Alloy Analyzer performs **bounded model checking** by translating specifications to SAT problems and searching for instances or counterexamples within a finite scope. Alloy 6 adds native support for **behavioral modeling** with mutable state and linear temporal logic (LTL).

Reference: [Practical Alloy](https://practicalalloy.github.io/) and [alloytools.org](https://alloytools.org).

---

## 2. Workflows

### 2.1 Writing a New Specification

Follow these steps when authoring an Alloy model from scratch:

1. **Understand the domain.** Ask about entities, relationships, invariants, and whether the system has dynamic behavior (state changes over time).
2. **Choose the modeling approach.**
   - *Structural only*: static relationships, constraints, and properties (no `var`).
   - *Behavioral*: mutable state with temporal logic (uses `var`, `always`, `eventually`, events).
3. **Write the structural skeleton.** Declare signatures and fields with explicit multiplicities. Add facts for domain constraints.
4. **Validate early.** Add a `run {}` command and mentally trace through the expected instances after every structural addition. If the Analyzer is available, run it.
5. **If behavioral:** add `var` declarations, an `init` predicate, event predicates (each with guard, effect, and frame conditions), a `stutter` predicate, and a `transitions` fact.
6. **Write properties.** Express safety as `assert { always P }` and liveness as `assert { fairness implies eventually P }`.
7. **Add `check` commands** with appropriate scopes and `expect` annotations to document expected outcomes.
8. **Run the Analyzer** if available (see Section 8).

### 2.2 Reviewing an Existing Specification

When reviewing `.als` files, check for these issues categorized by severity:

**Critical (likely bugs):**
- Missing frame conditions in event predicates (unmaintained `var` relations change freely)
- Missing stuttering event in the transitions disjunction
- Liveness assertions without fairness assumptions (likely vacuously satisfied)
- `always P` where `after always P` was intended (contradicts current state)

**Structural issues:**
- Over-constrained facts making the spec unsatisfiable -- run `run {}` to verify non-trivial instances exist
- Insufficient scope on `check` commands (default 3 atoms / 10 steps may hide bugs)
- Use of `util/ordering` where flexible scope is needed (it forces exact scope)
- Scope inheritance surprises (singleton `one sig` consuming parent scope budget)

**Subtle issues:**
- Subset `in` operator vacuously true when LHS is empty -- use `some X & Y` instead
- Quantifying over mutable sets outside `always` (evaluates only at initial state)
- Implicit field multiplicity (always write `one`, `set`, etc. explicitly)

---

## 3. Alloy 6 Language Reference

### 3.1 Signatures and Fields

Signatures declare sets of atoms. Fields declare relations between signatures.

```alloy
// Signature modifiers:
sig Object {}                        // top-level signature
abstract sig Object {}               // abstract: must be partitioned by extensions
one sig Root extends Dir {}          // singleton: exactly one atom
some sig Config {}                   // at least one atom
lone sig Optional {}                 // zero or one atom

// Extension hierarchy (disjoint partitioning):
abstract sig Object {}
sig Dir extends Object {
  entries : set Entry                 // zero or more Entries per Dir
}
sig File extends Object {}           // disjoint from Dir

// Fields as relations:
sig Entry {
  object : one Object,               // exactly one Object per Entry
  name   : one Name                  // exactly one Name per Entry
}
```

**Subset signatures** (non-disjoint subsets, declared with `in`):

```alloy
sig uploaded in File {}              // subset of File, not necessarily disjoint
sig trashed in uploaded {}           // subset of uploaded
sig A in B + C {}                    // subset of the union B + C
```

**Mutable declarations** (Alloy 6 -- value changes across states):

```alloy
var sig uploaded in File {}          // mutable subset signature
sig Node {
  succ : one Node,                   // immutable field
  var inbox : set Id,                // mutable field
  var outbox : set Id                // mutable field
}
```

**Higher-arity fields** (ternary and beyond):

```alloy
sig Node {
  var inbox : Type -> Node           // ternary: Node -> Type -> Node
}
```

**Signature facts** (constraints scoped to each atom of the signature):

```alloy
sig Dir extends Object {
  entries : set Entry
} {
  all disj e1, e2 : entries | e1.name != e2.name   // implicit `this`
}
```

**Field multiplicities:**

| Keyword | Meaning |
|---------|---------|
| `one`   | Exactly one target per source atom |
| `lone`  | Zero or one target |
| `some`  | One or more targets |
| `set`   | Zero or more targets (unrestricted) |

Always state multiplicity explicitly, even when `one` is the default.

### 3.2 The Many Uses of `disj`

The `disj` keyword appears in four distinct contexts in Alloy:

**1. In quantifiers -- bind distinct variables:**

```alloy
all disj x, y : Key | no x.lock & y.lock
// x and y are always different Key atoms
```

This replaces the verbose `all x, y : Key | x != y implies ...` pattern.

**2. On field declarations -- injectivity constraint:**

```alloy
sig Key {
  lock : disj some Lock
}
// No two Keys share a Lock. Equivalent to:
//   all disj x, y : Key | no x.lock & y.lock
// If lock is var, this holds in every state.
```

**3. Between field names -- per-atom disjointness:**

```alloy
sig Key {
  disj lock, lock2 : one Lock
}
// For each Key atom, lock and lock2 map to different Lock atoms.
```

**4. As a predicate -- n-way disjointness test:**

```alloy
all disj x, y, z, w : Key |
  disj[x.lock, y.lock, z.lock, w.lock]
// disj[] accepts any number of arguments.
```

### 3.3 Relational Logic Operators

**Set operators** (operands must have the same arity):

| Operator | Meaning |
|----------|---------|
| `+`      | Union |
| `&`      | Intersection |
| `-`      | Difference |

**Relational operators:**

| Operator | Name | Notes |
|----------|------|-------|
| `.`      | Dot-join (composition) | n-ary `.` m-ary = (n+m-2)-ary; cannot compose two unary |
| `[]`     | Box join | `R[S]` = `S.R` (alternative syntax) |
| `->`     | Cartesian product | Builds higher-arity relations |
| `~`      | Transpose | Binary relations only |
| `^`      | Transitive closure | Binary relations only |
| `*`      | Reflexive-transitive closure | `*R = ^R + iden` |
| `<:`     | Domain restriction | Left operand must be unary |
| `:>`     | Range restriction | Right operand must be unary |
| `++`     | Override | Same arity; replaces tuples matching first column |

**Dot-join examples** (the most important operator):

```alloy
e.name            // name of entry e (forward navigation)
d.entries         // set of entries in directory d
entries.e         // set of directories containing entry e (backward)
d.entries.object  // objects in entries of d (chained)
Entry.name        // all names of all entries (join on entire sig)
```

**Atomic formulas** (cardinality checks on expressions):

| Keyword | Meaning |
|---------|---------|
| `no R`   | R is empty (zero tuples) |
| `some R` | R is non-empty |
| `lone R` | R has at most one tuple |
| `one R`  | R has exactly one tuple |

**Comparison formulas:**

| Operator | Meaning |
|----------|---------|
| `R in S` | R is a subset of S (vacuously true if R is empty) |
| `R = S`  | R equals S |
| `R != S` | R does not equal S |

**Boolean connectives:**

| Symbol | Keyword | Meaning |
|--------|---------|---------|
| `!`    | `not`     | Negation |
| `&&`   | `and`     | Conjunction |
| `\|\|` | `or`      | Disjunction |
| `=>`   | `implies`  | Implication (optional `else` clause) |
| `<=>` | `iff`      | Biconditional |

`P implies Q else T` means `(P and Q) or (not P and T)`.

**Quantifiers:**

| Quantifier | Meaning |
|------------|---------|
| `all x : S \| F`  | F true for every x in S |
| `some x : S \| F` | F true for at least one x in S |
| `no x : S \| F`   | F false for all x in S |
| `lone x : S \| F` | F true for at most one x in S |
| `one x : S \| F`  | F true for exactly one x in S |

Quantifiers can range over any expression, not just signatures:

```alloy
all d : Dir, disj x, y : d.entries | x.name != y.name
```

**Other expression constructs:**

- `let name = expr | formula` -- name a sub-expression
- `{ x : S | F }` -- set comprehension (builds a relation)
- `{ x : A, y : B | F }` -- multi-variable set comprehension (binary relation)

**Predefined relations:**

| Relation | Meaning |
|----------|---------|
| `none`   | Empty unary set |
| `univ`   | Set of all atoms |
| `iden`   | Identity relation on `univ` (all atoms to themselves) |

To restrict `iden` to a signature: `A <: iden` or `iden & (A -> A)`.

**Operator precedence** (highest to lowest):

1. Unary: `~`, `^`, `*`, `!`/`not`, `no`/`some`/`lone`/`one` (on expressions)
2. Dot-join: `.`
3. Box join: `[]`
4. Restriction: `<:`, `:>`
5. Arrow product: `->`
6. Override: `++`
7. Intersection: `&`
8. Union/difference: `+`, `-`
9. Comparison: `in`, `=`, `!=`, `<`, `>`, `=<`, `>=`
10. Negation: `not`, `!`
11. Conjunction: `and`, `&&`
12. Implication: `implies`/`=>` (with optional `else`)
13. Biconditional: `iff`, `<=>`
14. Quantifiers: `all`, `some`, `no`, `lone`, `one`
15. Let: `let`
16. Temporal sequence: `;` (lowest precedence)

### 3.4 Facts, Predicates, Functions, Assertions

```alloy
// Fact: always assumed true, constrains all instances
fact no_shared_entries {
  all e : Entry | lone entries.e
}

// Predicate: reusable named formula, true only when invoked
pred reachable [o : Object] {
  o in Root + Root.^(entries.object)
}

// Function: reusable named expression returning a relation
fun descendants [o : Object] : set Object {
  o.^(entries.object)
}

// Assertion: property to verify (negated internally by check)
assert no_partitions {
  all o : Object | reachable[o]
}
```

### 3.5 Commands

**Two forms:**

```alloy
// Inline constraint
run show { some Dir } for 4
check safety { always some_property } for 6

// Named reference (predicate or assertion)
run reachable for 4
check no_partitions for 6
```

**Pitfall:** `check no_partitions {}` checks the empty constraint (always true), NOT the assertion. Omit the braces: `check no_partitions`.

**Scope syntax:**

| Syntax | Meaning |
|--------|---------|
| `for N` | Default scope of N atoms per top-level signature (default: 3) |
| `for N but M Sig` | Override scope for specific signature |
| `for N but exactly M Sig` | Exact scope (not upper bound) |
| `for 3 Object, 2 Name` | All scopes specified explicitly |
| `for N but K steps` | Bound trace prefix to K steps (default: 10) |
| `for N but 1.. steps` | Unbounded temporal model checking |

**Documenting expected outcomes:**

```alloy
check bad_property for 6 expect 1    // expect counterexample found
check good_property for 6 expect 0   // expect no counterexample
```

**Scope inheritance rules:**
- Overall scope applies to top-level signatures only.
- `one sig Root extends Dir` consumes 1 atom from `Dir`'s scope budget.
- If all extensions have exact scopes, parent scope auto-increases to their sum.

### 3.6 Temporal Operators (Alloy 6)

**Future-time operators:**

| Operator | Meaning |
|----------|---------|
| `after F` | F holds in the next state |
| `always F` | F holds in current and all future states |
| `eventually F` | F holds in current or some future state |
| `F until G` | G becomes true eventually, F holds until then |
| `F releases G` | G holds until and including when F becomes true, or G holds forever |

**Past-time operators:**

| Operator | Meaning |
|----------|---------|
| `before F` | F held in the previous state (false at state 0) |
| `once F` | F held in some past state (including current) |
| `historically F` | F held in all past states (including current) |
| `F since G` | G was true at some past point, F has held since |
| `F triggered G` | G has held since F was last true, or G has always held |

**Prime operator:** `expr'` evaluates `expr` in the next state. Equivalent to `after expr`. Double prime `''` means two states ahead.

**Sequence operator:** `P ; Q` means `P and after Q`. Lowest precedence of all operators. Useful for encoding trace scenarios.

**Key rules:**
- Top-level formulas (outside temporal operators) are evaluated at state 0.
- Past-time operators at the top level degenerate: `historically P` at state 0 is just `P`.
- Past-time operators are only useful inside future-time operators (e.g., `always (... once ...)`).
- `before` is always false at state 0.
- Alloy traces are infinite (finite behavior is modeled via stuttering self-loops).

### 3.7 Module System

```alloy
module mymodule                      // optional module declaration

open util/ordering[Id]               // import with type parameter
open util/relation as rel            // import with alias
open mylib/graph[Node]               // parametrized module instantiation
```

**Parametrized modules:**

```alloy
module graph[node]
pred dag [r : node -> node] {
  all n : node | n not in n.^r
}
```

Import with: `open graph[Object]`.

**Private declarations:**

```alloy
private one sig Aux { helper : A -> B }
```

Private elements are inaccessible from importing modules.

**Standard library modules:**
- `util/ordering[T]` -- provides `first`, `last`, `next`, `lt`, `gt`, etc. **Caveat:** forces exact scope on `T`.
- `util/relation` -- `dom`, `ran`, `total`, `surjective`, etc.
- `util/graph[T]` -- graph predicates.

### 3.8 Reserved Keywords

These identifiers cannot be used as signature, field, predicate, or function names. Using them produces cryptic parse errors.

| Category | Keywords |
|----------|----------|
| Declarations | `sig`, `extends`, `in`, `abstract`, `one`, `lone`, `some`, `var`, `enum` |
| Definitions | `pred`, `fun`, `fact`, `assert`, `let` |
| Commands | `run`, `check`, `for`, `but`, `expect`, `exactly`, `steps` |
| Logical | `all`, `no`, `not`, `and`, `or`, `implies`, `else`, `iff`, `disj` |
| Temporal | `always`, `eventually`, `after`, `before`, `until`, `releases`, `since`, `triggered`, `once`, `historically` |
| Relational | `set`, `seq`, `none`, `univ`, `iden`, `Int` |
| Modules | `module`, `open`, `as`, `private` |

**Common trap:** `seq` is reserved (it refers to Alloy's sequence type). If you need a "sequence number" field, use `seqNum`, `seqNo`, or `idx` instead.

```alloy
// WRONG: parse error
sig Event { seq : one Int }

// RIGHT: rename to avoid the reserved word
sig Event { seqNum : one Int }
```

---

## 4. Specification Styles

Alloy supports three specification styles. Prefer **navigational** as the default.

**Navigational style (preferred):** Minimal quantification, heavy use of `.` for relation navigation.

```alloy
// "Every entry belongs to at most one directory"
all e : Entry | lone entries.e
```

**Pointwise / first-order style:** Explicit quantification over individual atoms and tuple membership.

```alloy
// Same constraint, pointwise:
all x, y : Dir, e : Entry |
  x->e in entries and y->e in entries implies x = y
```

**Pointfree style:** No quantifiers at all; purely relational operators.

```alloy
// Same constraint, pointfree:
entries.~entries in iden
```

Pointfree is elegant but often opaque. Use it only when the navigational form is more complex.

---

## 5. Canonical Patterns and Idioms

### 5.1 Structural Patterns

**Reachability via transitive closure:**

```alloy
fun descendants [o : Object] : set Object {
  o.^(entries.object)         // transitive closure of navigation
}
fun allReachable : set Object {
  Root.*(entries.object)      // reflexive-transitive: includes Root itself
}
```

**Override pattern** (updating a relation for one key):

```alloy
entries' = entries ++ (d -> newEntries)
// Replaces d's entries, keeps all other directories' entries unchanged
```

**Total ordering without `util/ordering`** (avoids exact scope limitation):

```alloy
sig Node {
  next : lone Node
}
one sig first, last in Node {}
fact ordering {
  no next.first           // nothing precedes first
  no last.next            // nothing follows last
  Node - first in first.^next  // all nodes reachable from first
}
```

### 5.2 Behavioral Modeling Template

The canonical structure for any behavioral Alloy model:

```alloy
// --- State ---
var sig uploaded in File {}
sig File {
  var shared : set Token
}

// --- Initial state ---
pred init {
  no uploaded
  all f : File | no f.shared
}

// --- Events (each has guard + effect + frame conditions) ---
pred upload [f : File] {
  // Guard
  f not in uploaded
  // Effect
  uploaded' = uploaded + f
  // Frame conditions (EVERY other mutable relation)
  shared' = shared
}

// --- Stuttering (mandatory) ---
pred stutter {
  uploaded' = uploaded
  shared' = shared
}

// --- Transition system ---
fact transitions {
  init and always (
    (some f : File | upload[f])
    or (some f : File | delete[f])
    or stutter
  )
}
```

**Frame condition discipline:** Every event predicate must explicitly state `relation' = relation` for every mutable relation it does not modify. Omitting a frame condition leaves the relation unconstrained -- the most common source of bugs.

**Stuttering is mandatory.** It ensures:
1. Composability with concurrent systems
2. Finite behaviors extend to infinite traces (required by Alloy's semantics)
3. Deadlocked states do not make liveness properties vacuously true

### 5.3 Protocol Design Patterns

**Ring topology:**

```alloy
sig Node {
  succ : one Node
}
fact ring {
  all n : Node | Node in n.^succ
}
```

**Messages as tuples (recommended approach):**

```alloy
abstract sig Type {}
one sig Candidate, Elect extends Type {}

sig Node {
  succ : one Node,
  var inbox : Type -> Node    // each tuple IS a message
}
```

No message atoms needed. No scope issues. Order-of-magnitude faster analysis. Use this when message structure is simple (1-2 fields).

**Messages as signatures** (when messages have complex structure):

```alloy
abstract sig Message { payload : one Node }
sig CandidateMsg, ElectedMsg extends Message {}
```

Requires generator axioms and uniqueness to avoid scope problems:

```alloy
fact generator {
  all n : Node | some m : CandidateMsg | m.payload = n
  all n : Node | some m : ElectedMsg | m.payload = n
}
fact unique {
  all disj m1, m2 : CandidateMsg | m1.payload != m2.payload
  all disj m1, m2 : ElectedMsg | m1.payload != m2.payload
}
```

**Derived state via temporal functions** (eliminates frame conditions for computed properties):

```alloy
fun Elected : set Node {
  { n : Node | once (before (some (Elect -> n) & n.inbox)
                     and no (Elect -> n) & n.inbox) }
}
```

### 5.4 Verification Patterns

**Safety** (something bad never happens):

```alloy
assert shared_are_accessible {
  always (shared.Token in uploaded - trashed)
}
check shared_are_accessible for 5
```

**Liveness** (something good eventually happens -- requires fairness):

```alloy
pred fairness {
  all n : Node |
    (eventually always enabled[n]) implies
    (always eventually acts[n])
}
assert eventually_elected {
  fairness implies eventually (some Elected)
}
check eventually_elected for 4 but 20 steps
```

**Undo property:**

```alloy
assert restore_undoes_delete {
  all f : File | always (
    delete[f] and after restore[f] implies
    uploaded'' = uploaded and trashed'' = trashed
  )
}
```

**One-shot property:**

```alloy
assert single_download {
  all t : Token | always (
    download[t] implies after always not download[t]
  )
}
```

**Releases pattern** (action blocked until precondition recurs):

```alloy
assert empty_after_restore {
  all f : File | always (
    delete[f] implies
    after ((restore[f] or upload[f]) releases not delete[f])
  )
}
```

**Inductive invariant** (dramatically faster than unbounded temporal check):

```alloy
pred inv { shared.Token in uploaded - trashed }

// Refactor init and transitions as predicates (not facts) for induction
pred next {
  (some f : File | upload[f]) or
  (some f : File | delete[f]) or
  stutter
}

assert initiation { init implies inv }
assert preservation { (inv and next) implies after inv }

check initiation for 10 but 1 steps
check preservation for 10 but 2 steps
```

**When to prefer the inductive approach:**
- When the model uses **integer arithmetic** (`Int` fields, `plus`, `minus`, `mul`). Temporal checks with integers are extremely expensive because the SAT encoding multiplies bitwidth across every step. A `check` at scope 3 with 5-bit integers and 10 steps can easily time out (5+ minutes), while the equivalent inductive check at scope 10 with 2 steps finishes in seconds.
- When temporal checks **time out** at reasonable scopes. The inductive approach reduces a temporal safety property to two small bounded checks: `initiation` (1 step) proves the invariant holds initially, and `preservation` (2 steps) proves any single transition preserves it. Together they imply the property holds at all reachable states.
- **Integer scope note:** `for N Int` means N **bits** (including sign bit), not N atoms. `5 Int` gives the range -16..15. Always set integer bitwidth wide enough to avoid silent overflow.

### 5.5 Event Depiction Idiom

Makes events visible in the Analyzer's trace visualizer.

```alloy
enum Event { UploadEv, DeleteEv, RestoreEv, StutterEv }

fun upload_happens : Event -> File {
  { e : UploadEv, f : File | upload[f] }
}
fun delete_happens : Event -> File {
  { e : DeleteEv, f : File | delete[f] }
}
fun stutter_happens : set Event {
  { e : StutterEv | stutter }
}

// Aggregate for visualization and transition simplification
fun events : set Event {
  stutter_happens +
  (upload_happens + delete_happens).File
}

// Simplify the transitions fact:
fact transitions { init and always some events }

// Check mutual exclusion of events:
check at_most_one { always lone events } for 3
```

No performance penalty -- derived functions are only computed during visualization unless referenced in formulas.

### 5.6 Encoding Trace Scenarios

Use the `;` (sequence) operator to describe specific execution traces:

**Event-oriented style (simplest):**

```alloy
run scenario {
  some f : File, t : Token {
    upload[f] ; share[f, t] ; download[t] ; delete[f] ; always stutter
  }
} for 1 File, 1 Token
```

**State-oriented style (most precise):**

```alloy
run scenario {
  some f : File, disj t0, t1 : Token {
    File = f and Token = t0 + t1
    no uploaded ; uploaded = f ; uploaded = f ; always no uploaded
    no shared   ; no shared    ; shared = f->t0 ; always no shared
  }
} for 1 File, 2 Token
```

Always constrain the tail of the trace (e.g., `always stutter`) to prevent unexpected continuations.

---

## 6. Common Pitfalls

### Pitfall 1: Missing frame conditions

Unmaintained mutable relations change arbitrarily between states.

```alloy
// WRONG: shared can change freely during upload
pred upload [f : File] {
  f not in uploaded
  uploaded' = uploaded + f
}

// RIGHT: explicitly preserve all other mutable relations
pred upload [f : File] {
  f not in uploaded
  uploaded' = uploaded + f
  trashed' = trashed
  shared' = shared
}
```

### Pitfall 2: Missing stuttering in transitions

Without stuttering, deadlocked states have no valid infinite continuations, making the entire specification vacuously true for properties about those states.

```alloy
// WRONG: deadlock if no event is enabled
fact transitions {
  init and always (some f : File | upload[f] or delete[f])
}

// RIGHT: always include stutter
fact transitions {
  init and always (
    (some f : File | upload[f] or delete[f])
    or stutter
  )
}
```

### Pitfall 3: `always P` when you mean `after always P`

Inside an event predicate, `always P` includes the current state, which may contradict the event's own effect.

```alloy
// WRONG: says download[t] is false NOW (contradicts itself)
pred download [t : Token] {
  ...
  always not download[t]
}

// RIGHT: prohibition starts in the next state
pred download [t : Token] {
  ...
  after always not download[t]
}
```

### Pitfall 4: Liveness without fairness

Liveness properties are trivially satisfied by infinite stuttering unless fairness excludes it.

```alloy
// WRONG: satisfied by never doing anything
assert progress { eventually some uploaded }
check progress

// RIGHT: add fairness as a premise
pred fairness { always eventually (some f : File | upload[f]) or no (File - uploaded) }
assert progress { fairness implies eventually some uploaded }
check progress
```

### Pitfall 5: Over-constrained facts

Adding too many constraints can make the specification unsatisfiable, causing all `check` commands to trivially pass (no counterexample can exist if no instance exists).

```alloy
// Always validate satisfiability after modifying facts:
run sanity_check {} for 5
// If this finds no instance, the spec is over-constrained.
```

### Pitfall 6: Insufficient scope

The default scope of 3 atoms and 10 steps may hide counterexamples.

```alloy
// Passes at scope 3, fails at scope 5:
check no_partitions for 3        // no counterexample (false confidence)
check no_partitions for 5        // counterexample found!

// For liveness, increase step bound:
check liveness_prop for 4 but 30 steps
```

### Pitfall 7: `util/ordering` forces exact scope

`open util/ordering[Id]` forces the scope of `Id` to be exact, preventing analysis at smaller sizes.

```alloy
// If flexible scope matters, declare ordering manually:
sig Id {
  next : lone Id
}
one sig first, last in Id {}
fact ordering {
  no next.first
  no last.next
  Id - first in first.^next
}
```

### Pitfall 8: Quantifying over mutable sets outside `always`

Top-level formulas are evaluated at state 0 only.

```alloy
// WRONG: Elected is likely empty at state 0
all n : Elected | some n.inbox

// RIGHT: place inside always so it re-evaluates each state
always (all n : Elected | some n.inbox)
```

### Pitfall 9: Subset `in` is vacuously true when LHS is empty

The `in` operator means subset, not membership. An empty set is a subset of everything.

```alloy
// WRONG: true even when Root has zero atoms (lone sig)
Root in Dir

// RIGHT: use intersection to test non-empty overlap
some Root & Dir

// Or be explicit about non-emptiness:
some Root and Root in Dir
```

### Pitfall 10: Not validating after modifying facts

After strengthening any fact, always run an unconstrained `run` command to ensure the specification is still satisfiable.

```alloy
// Add this as a standing validation command:
run show {} for 5
```

If the Analyzer reports "No instance found" for an unconstrained `run`, the facts are contradictory.

### Pitfall 11: Higher-order quantification is not supported

Alloy's SAT-based analyzer only supports **first-order** quantification -- quantifying over individual atoms. Quantifying over *sets* of atoms (`some x : set Sig | ...`) is second-order and triggers a "cannot skolemize" error or silent analysis failure.

```alloy
// WRONG: higher-order quantification (quantifying over sets)
pred step [s : Sim] {
  some newEvents : set Event |
    newEvents = processAction[s]
}

// RIGHT: restructure to avoid set-valued quantification
// Option A: use lone/one if at most one result
pred step [s : Sim] {
  some newEvent : Event |
    newEvent = processAction[s]
}

// Option B: use a helper signature to hold the set
sig ActionResult {
  produced : set Event
}
pred step [s : Sim, ar : ActionResult] {
  ar.produced = processAction[s]
}

// Option C: use lone when "zero or one" suffices
sig ActionResult {
  descriptor : lone EventDescriptor
}
```

**Rule of thumb:** If you find yourself writing `some x : set Sig`, redesign the model. Introduce a helper signature with a `set`-valued field, or decompose the logic so each quantified variable binds a single atom.

### Pitfall 12: `after` is a formula operator, not an expression operator

The temporal operator `after` applies only to **formulas** (boolean). It cannot wrap a function call or relational expression. Use the **prime operator** (`'`) to get next-state values of expressions.

```alloy
fun peekNext [s : Sim] : lone Event {
  { e : s.queue | e.time = min[s.queue.time] }
}

// WRONG: after cannot wrap a fun call (expression)
pred peek_stable [s : Sim] {
  after peekNext[s] = peekNext[s]
}

// RIGHT: use primed relations inside a set comprehension
pred peek_stable [s : Sim] {
  { e : s.queue' | e.time = min[s.queue'.time] }
  =
  { e : s.queue | e.time = min[s.queue.time] }
}

// Also RIGHT: use after on the whole formula
pred peek_stable [s : Sim] {
  after (peekNext[s] = peekNext[s])
}
// But note: this compares next-state with next-state (both shifted),
// which is a tautology. See Pitfall 14 for this subtlety.
```

**Summary:** `after F` where F is a formula -- valid. `after expr` where expr returns a relation -- parse error. Use `expr'` (prime on the mutable relation inside the expression) instead.

### Pitfall 13: Predicate branching with conjoined `no`/`some` guards

When modeling conditional behavior in a predicate (e.g., "if a field is empty do X, otherwise do Y"), a conjunction of an implication and an existential quantifier over the same field creates an unsatisfiable predicate when the field is empty.

```alloy
sig ActionResult {
  descriptor : lone EventDescriptor
}

// WRONG: when no ar.descriptor, the `some d` branch is false,
// making the entire conjunction unsatisfiable
pred step [ar : ActionResult] {
  no ar.descriptor implies {
    queue' = queue - currentEvent
  }
  some d : ar.descriptor | {
    queue' = queue - currentEvent + makeEvent[d]
  }
}

// RIGHT: use explicit disjunction with guards inside each branch
pred step [ar : ActionResult] {
  {
    no ar.descriptor
    queue' = queue - currentEvent
  }
  or
  {
    some d : ar.descriptor | {
      queue' = queue - currentEvent + makeEvent[d]
    }
  }
}
```

**Why it fails:** `some d : ar.descriptor | P` is `false` when `ar.descriptor` is empty -- there is no `d` to witness the existential. Conjoining this with `no ar.descriptor implies Q` means: when the descriptor is absent, Q holds *and* false -- contradiction. Use `or`-branches with the guard condition *inside* each branch.

### Pitfall 14: Temporal operators shift evaluation uniformly

All temporal operators (`always`, `eventually`, `after`, `until`, etc.) shift the evaluation state for **all** sub-expressions uniformly. There is no way to "freeze" an outer reference at the current state while shifting an inner reference to a future state within a single temporal operator.

```alloy
sig Sim {
  var curTime : one Int
}

// WRONG: both curTime references evaluate at the SAME future state
// This is the tautology  t > t  which is always false
assert time_advances {
  eventually (Sim.curTime > Sim.curTime)
}

// WRONG: let does NOT help -- the binding is also shifted
assert time_advances {
  let t = Sim.curTime |
    eventually (Sim.curTime > t)
}
// Inside `eventually`, both Sim.curTime and t evaluate at the shifted state

// RIGHT: use after with prime to compare adjacent states
assert time_advances_per_step {
  always (not stutter implies Sim.curTime' > Sim.curTime)
}

// RIGHT: use past-time operators inside future-time context
assert time_advances_past {
  always (not stutter implies Sim.curTime > (before Sim.curTime))
}
// `before` looks back one state from the current evaluation point
```

**Key insight:** To compare values across different states, use either: (1) the prime operator (`expr'` vs `expr`) to compare adjacent states, or (2) past-time operators (`before`, `once`, `historically`) inside a future-time context to look backward from the shifted evaluation point. Never rely on `let` bindings or nested temporal operators to "capture" a value from an outer state.

---

## 7. Complete Examples

### Example A: Structural -- File System

A model of a hierarchical file system with directories, files, entries, and names. Demonstrates structural modeling, transitive closure, and property verification.

```alloy
module filesystem

// --- Signatures ---

abstract sig Object {}

sig Dir extends Object {
  entries : set Entry
}

sig File extends Object {}

one sig Root extends Dir {}

sig Entry {
  object : one Object,
  name : one Name
}

sig Name {}

// --- Facts ---

// Every entry belongs to at most one directory
fact no_shared_entries {
  all e : Entry | lone entries.e
}

// No two entries in the same directory share a name
fact unique_names {
  all d : Dir, disj e1, e2 : d.entries | e1.name != e2.name
}

// Every non-Root object is referenced by exactly one entry
fact no_dangling_objects {
  Entry.object = Object - Root
}

// No object is its own descendant (acyclicity)
fact acyclic {
  all o : Object | o not in descendants[o]
}

// Every object is reachable from Root
fact all_reachable {
  Object = Root + descendants[Root]
}

// --- Functions ---

fun descendants [o : Object] : set Object {
  o.^(entries.object)
}

fun contents [d : Dir] : set Object {
  d.entries.object
}

// --- Predicates ---

pred empty_dir [d : Dir] {
  no d.entries
}

// --- Assertions ---

// The file system has no partitions (everything is connected)
assert no_partitions {
  all o : Object | o in Root + descendants[Root]
}

// Root is never contained in another directory
assert root_is_top {
  no entries.object.Root
}

// --- Commands ---

run show {} for 4
run show_empty { some d : Dir | empty_dir[d] } for 4

check no_partitions for 6 expect 0
check root_is_top for 6 expect 0
```

### Example B: Behavioral -- File Sharing Application

A model of a cloud file sharing app where files can be uploaded, deleted, restored, shared via tokens, and downloaded. Demonstrates mutable state, temporal logic, frame conditions, safety/liveness, and the event depiction idiom.

```alloy
module filesharing

// --- Signatures ---

sig File {
  var shared : set Token
}

sig Token {}

var sig uploaded in File {}
var sig trashed in uploaded {}

// --- Initial state ---

pred init {
  no uploaded
  no trashed
  no shared
}

// --- Events ---

pred upload [f : File] {
  // Guard
  f not in uploaded
  // Effects
  uploaded' = uploaded + f
  // Frame conditions
  trashed' = trashed
  shared' = shared
}

pred delete [f : File] {
  // Guard
  f in uploaded - trashed
  // Effects
  trashed' = trashed + f
  // Frame conditions
  uploaded' = uploaded
  shared' = shared
}

pred restore [f : File] {
  // Guard
  f in trashed
  // Effects
  trashed' = trashed - f
  // Frame conditions
  uploaded' = uploaded
  shared' = shared
}

pred share [f : File, t : Token] {
  // Guard
  f in uploaded - trashed
  historically t not in File.shared        // token never used before
  // Effects
  shared' = shared + f -> t
  // Frame conditions
  uploaded' = uploaded
  trashed' = trashed
}

pred download [t : Token] {
  // Guard
  some shared.t & (uploaded - trashed)     // file is accessible
  // Effects (no state change -- read-only action)
  uploaded' = uploaded
  trashed' = trashed
  shared' = shared
}

pred empty {
  // Guard
  some trashed
  // Effects
  uploaded' = uploaded - trashed
  trashed' = trashed - trashed             // i.e., no trashed'
  shared' = shared - trashed -> Token      // remove shares of trashed files
}

pred stutter {
  uploaded' = uploaded
  trashed' = trashed
  shared' = shared
}

// --- Transition system ---

fact transitions {
  init and always (
    (some f : File | upload[f] or delete[f] or restore[f])
    or (some f : File, t : Token | share[f, t])
    or (some t : Token | download[t])
    or empty
    or stutter
  )
}

// --- Safety properties ---

assert shared_are_accessible {
  always (shared.Token in uploaded - trashed)
}

assert trashed_are_uploaded {
  always (trashed in uploaded)
}

// --- Liveness properties ---

pred fairness_on_empty {
  (eventually always some trashed) implies
  (always eventually empty)
}

assert trash_eventually_emptied {
  fairness_on_empty implies always eventually no trashed
}

// --- Undo property ---

assert restore_undoes_delete {
  all f : File | always (
    delete[f] and after restore[f] implies
    uploaded'' = uploaded and trashed'' = trashed and shared'' = shared
  )
}

// --- One-shot property ---

assert single_download_per_token {
  all t : Token | always (
    download[t] implies after always not download[t]
  )
}

// --- Event depiction (for visualization) ---

enum Event { UploadEv, DeleteEv, RestoreEv, ShareEv, DownloadEv, EmptyEv, StutterEv }

fun upload_happens : Event -> File {
  { e : UploadEv, f : File | upload[f] }
}
fun delete_happens : Event -> File {
  { e : DeleteEv, f : File | delete[f] }
}
fun restore_happens : Event -> File {
  { e : RestoreEv, f : File | restore[f] }
}
fun share_happens : Event -> File -> Token {
  { e : ShareEv, f : File, t : Token | share[f, t] }
}
fun download_happens : Event -> Token {
  { e : DownloadEv, t : Token | download[t] }
}
fun empty_happens : set Event {
  { e : EmptyEv | empty }
}
fun stutter_happens : set Event {
  { e : StutterEv | stutter }
}

fun events : set Event {
  empty_happens + stutter_happens
  + (upload_happens + delete_happens + restore_happens).File
  + download_happens.Token
  + (share_happens.Token).File
}

// --- Commands ---

run show {} for 3 but 10 steps
run scenario_share_then_delete {
  some f : File, t : Token {
    upload[f] ; share[f, t] ; delete[f] ; download[t] ; always stutter
  }
} for 2 but 8 steps expect 0      // download should fail: file is trashed

check shared_are_accessible for 5 but 15 steps expect 0
check trashed_are_uploaded for 5 but 15 steps expect 0
check restore_undoes_delete for 4 but 10 steps expect 0
check trash_eventually_emptied for 3 but 20 steps
```

### Example C: Protocol -- Leader Election on a Ring

A model of the Chang-Roberts leader election protocol on a unidirectional ring. Demonstrates the messages-as-tuples pattern, derived state via temporal functions, ring topology, safety and liveness with fairness.

```alloy
module leaderelection

// --- Network topology ---

sig Id {
  gt : set Id                           // manual total order (avoids util/ordering)
}

abstract sig Type {}
one sig Candidate, Elect extends Type {}

sig Node {
  succ : one Node,
  id : one Id,
  var inbox : Type -> Id,
  var outbox : Type -> Id
}

// --- Topology constraints ---

fact ring {
  all n : Node | Node in n.^succ       // succ forms a connected ring
}

fact unique_ids {
  all disj n1, n2 : Node | n1.id != n2.id
}

fact id_ordering {
  all i : Id | i not in i.^gt           // irreflexive
  all disj i, j : Id | i in j.gt or j in i.gt  // total
  all i, j, k : Id | (i in j.gt and j in k.gt) implies i in k.gt  // transitive
}

// --- Derived state ---

// A node is elected when an Elect message bearing its own id
// is consumed from its inbox (was present, now absent).
fun Elected : set Node {
  { n : Node | once (
    before (some (Elect -> n.id) & n.inbox)
    and no (Elect -> n.id) & n.inbox
  )}
}

// --- Initial state ---

pred init {
  no inbox
  no outbox
}

// --- Events ---

pred initiate [n : Node] {
  // Guard: node has not yet sent a candidate message
  historically no (Candidate -> n.id) & n.outbox
  // Effects
  outbox' = outbox + n -> Candidate -> n.id
  inbox' = inbox + n.succ -> Candidate -> n.id
  // (message appears in successor's inbox)
}

pred process [n : Node, t : Type, i : Id] {
  // Guard: message is in inbox
  t -> i in n.inbox
  // Remove from inbox
  let msg_removed = inbox - n -> t -> i |
  let new_outbox = outbox |
  {
    // Candidate message: forward if sender id > my id
    t = Candidate implies {
      i in n.id.gt implies {
        // Sender id is greater: forward the candidate
        inbox' = msg_removed + n.succ -> Candidate -> i
        outbox' = new_outbox + n -> Candidate -> i
      } else i = n.id implies {
        // My own id came back: I am the leader, send Elect
        inbox' = msg_removed + n.succ -> Elect -> i
        outbox' = new_outbox + n -> Elect -> i
      } else {
        // Sender id is smaller: discard
        inbox' = msg_removed
        outbox' = new_outbox
      }
    }
    // Elect message: forward unless it is for me
    t = Elect implies {
      i != n.id implies {
        inbox' = msg_removed + n.succ -> Elect -> i
        outbox' = new_outbox + n -> Elect -> i
      } else {
        // Elect message for me: consume it (triggers Elected)
        inbox' = msg_removed
        outbox' = new_outbox
      }
    }
  }
}

pred stutter {
  inbox' = inbox
  outbox' = outbox
}

// --- Transition system ---

fact transitions {
  init and always (
    (some n : Node | initiate[n])
    or (some n : Node, t : Type, i : Id | process[n, t, i])
    or stutter
  )
}

// --- Safety ---

assert at_most_one_leader {
  always (lone Elected)
}

// The elected node has the highest id
assert leader_has_max_id {
  always (all n : Elected | no n.id.gt & Node.id)
}

// --- Liveness (with fairness) ---

pred fairness {
  // Weak fairness: if a node can always eventually act, it does
  all n : Node |
    (eventually always (
      (historically no (Candidate -> n.id) & n.outbox)   // can initiate
      or some n.inbox                                      // can process
    ))
    implies
    (always eventually (
      initiate[n]
      or (some t : Type, i : Id | process[n, t, i])
    ))
}

assert eventually_a_leader {
  fairness implies eventually (some Elected)
}

// --- Commands ---

run show {} for 3 Node, 3 Id, 2 Type but 15 steps
run show_election {
  eventually some Elected
} for 3 Node, 3 Id, 2 Type but 15 steps

check at_most_one_leader for 4 Node, 4 Id, 2 Type but 20 steps expect 0
check leader_has_max_id for 4 Node, 4 Id, 2 Type but 20 steps expect 0
check eventually_a_leader for 3 Node, 3 Id, 2 Type but 25 steps
```

---

## 8. Analyzer Integration

### Detection

Check for the Alloy Analyzer in this order:

1. `alloy` command on `PATH`
2. `org.alloytools.alloy.dist.jar` in the project directory or common locations
3. Any `alloy*.jar` file in the project directory

### Invocation

```bash
# If alloy CLI is on PATH:
alloy exec <file.als>

# If alloy CLI is on PATH and using a markdown file:
alloy exec <file.md>

# If using the JAR directly:
java -jar <path-to-alloy.jar> <file.als>

# If using the JAR directly and using a markdown file:
java -jar <path-to-alloy.jar> <file.md>
```

### Interpreting Output

| Output | Meaning |
|--------|---------|
| "Instance found" | `run` command found a satisfying instance |
| "No instance found" | `run` command: spec may be over-constrained |
| "Counterexample found" | `check` command: assertion is violated |
| "No counterexample found" | `check` command: assertion holds within scope |

**If the Analyzer is not available:** produce the `.als` file and inform the user to open it in the Alloy Analyzer GUI or install the CLI tool.

---

## 9. File Conventions

- File extension: `.als`
- One module per file
- `module` declaration should match the filename (e.g., `module filesystem` in `filesystem.als`)
- Comments: `//` (line), `--` (line), `/* ... */` (block)
- Suggest placing specifications in a `models/` or `alloy/` directory within the project
- Use clear section comments (`// --- Signatures ---`, `// --- Events ---`, etc.) to organize the file
- When writing Alloy in a markdown file (`.md`), the file must start with a YAML header (frontmatter): three dashes on the first line, followed by a title field in YAML format, followed by three more dashes.
```yaml
---
title: [ModelTitle]
---
```
