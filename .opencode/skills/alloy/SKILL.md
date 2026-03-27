---
name: alloy
description: Write, review, and validate formal specifications of system designs, state machines, protocols, and algorithms using the Alloy 6 modeling language
license: MIT
compatibility: opencode
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

Alloy is a formal modeling language whose core principle is **everything is a relation**. The Alloy Analyzer performs **bounded model checking** by translating specifications to SAT problems and searching for instances or counterexamples within a finite scope. Alloy 6 adds native support for **behavioral modeling** with mutable state and linear temporal logic (LTL).

Reference: [Practical Alloy](https://practicalalloy.github.io/) and [alloytools.org](https://alloytools.org).

---

## 2. Workflows

### 2.1 Writing a New Specification

1. **Gather requirements.** Identify entities, relations, global constraints, which parts are mutable vs immutable, events that change state, safety properties (what must never happen), and liveness properties (what must eventually happen). If details are missing, make the smallest reasonable abstraction and state assumptions explicitly.
2. **Classify the problem.**
   - *Structural only*: signatures, fields, facts, functions, assertions (no `var`).
   - *Behavioral*: mutable state with `var`, action predicates, `init`, `stutter`, temporal assertions.
   - *Protocol*: separate immutable topology/configuration from mutable protocol state.
3. **Start with the smallest useful vocabulary.** Prefer abstract signatures, explicit multiplicities, `extends` for disjoint partitions. Avoid integers unless arithmetic is truly required.
4. **Validate immediately.** Add `run example {}` first. Check satisfiability before adding many facts. Re-run after every major strengthening to avoid vacuity.
5. **Add constraints incrementally.** Prefer navigational formulas using dot-join. Use helper `fun`/`pred` definitions for repeated concepts. Use `^` and `*` for reachability.
6. **For behavior, model actions explicitly.** Write `pred init`, one predicate per action (guard + effect + full frame conditions), `pred stutter`, and a `transitions` fact with `always (... or stutter)`.
7. **State properties separately.** Safety as `assert { always P }`. Liveness as `assert { fairness implies eventually P }`. Factor into inductive invariant when useful.
8. **Add targeted commands.** Positive scenarios with `run`. Negative examples with unsat `run`. Bounded checks with explicit scopes, step counts, and `expect` annotations.
9. **Run the Analyzer** if available (see Section 7).

### 2.2 Reviewing an Existing Specification

When reviewing `.als` files, check for issues by severity:

**Critical (likely bugs):**
- Missing frame conditions in event predicates (unmaintained `var` relations change freely)
- Missing stuttering event in the transitions disjunction
- Liveness assertions without fairness assumptions (likely vacuously satisfied)
- `always P` where `after always P` was intended (contradicts current state)

**Structural issues:**
- Over-constrained facts making the spec unsatisfiable -- run `run {}` to verify non-trivial instances exist
- Insufficient scope on `check` commands (default 3 atoms / 10 steps may hide bugs)
- `util/ordering` forces exact scope on the ordered signature
- Singleton `one sig` consuming parent scope budget

**Subtle issues:**
- Subset `in` vacuously true when LHS is empty -- use `some X & Y` instead
- Quantifying over mutable sets outside `always` (evaluates only at initial state)
- Implicit field multiplicity (always write `one`, `set`, etc. explicitly)
- Adding fairness only to make liveness pass is a modeling smell
- Helper singleton signatures for tests polluting the whole model

---

## 3. Language Quick Reference

This section covers aspects of Alloy that are frequently misused. For basic syntax (signatures, fields, set/relational operators, quantifiers, boolean connectives), rely on standard Alloy 6 knowledge.

### 3.1 Field Multiplicities

Always state multiplicity explicitly, even when `one` is the default:

| Keyword | Meaning |
|---------|---------|
| `one`   | Exactly one target per source atom |
| `lone`  | Zero or one target |
| `some`  | One or more targets |
| `set`   | Zero or more targets (unrestricted) |

### 3.2 The Four Uses of `disj`

**1. In quantifiers** -- bind distinct variables:

```alloy
all disj x, y : Key | no x.lock & y.lock
```

**2. On field declarations** -- injectivity constraint:

```alloy
sig Key { lock : disj some Lock }
// No two Keys share a Lock. If lock is var, holds in every state.
```

**3. Between field names** -- per-atom disjointness:

```alloy
sig Key { disj lock, lock2 : one Lock }
// For each Key atom, lock and lock2 map to different Lock atoms.
```

**4. As a predicate** -- n-way disjointness test:

```alloy
disj[x.lock, y.lock, z.lock]
```

### 3.3 Temporal Operators (Alloy 6)

**Future-time:**

| Operator | Meaning |
|----------|---------|
| `after F` | F holds in the next state |
| `always F` | F holds in current and all future states |
| `eventually F` | F holds in current or some future state |
| `F until G` | G becomes true eventually, F holds until then |
| `F releases G` | G holds until and including when F becomes true, or forever |

**Past-time:**

| Operator | Meaning |
|----------|---------|
| `before F` | F held in the previous state (false at state 0) |
| `once F` | F held in some past state (including current) |
| `historically F` | F held in all past states (including current) |
| `F since G` | G was true at some past point, F has held since |
| `F triggered G` | G has held since F was last true, or G has always held |

**Prime operator:** `expr'` evaluates `expr` in the next state. `''` means two states ahead.

**Sequence operator:** `P ; Q` means `P and after Q`. Lowest precedence of all operators.

**Key rules:**
- Top-level formulas (outside temporal operators) are evaluated at state 0 only.
- Past-time operators at the top level degenerate: `historically P` at state 0 is just `P`.
- Past-time operators are useful only inside future-time operators (e.g., `always (... once ...)`).
- `before` is always false at state 0.
- Alloy traces are infinite (finite behavior is modeled via stuttering self-loops).

### 3.4 Commands and Scopes

| Syntax | Meaning |
|--------|---------|
| `for N` | Default scope of N atoms per top-level signature |
| `for N but M Sig` | Override scope for specific signature |
| `for N but exactly M Sig` | Exact scope (not upper bound) |
| `for N but K steps` | Bound trace to K steps (default: 10) |
| `for N but 1.. steps` | Unbounded temporal model checking |

**`expect` annotations** document expected outcomes:

```alloy
check bad_property for 6 expect 1    // expect counterexample found
check good_property for 6 expect 0   // expect no counterexample
```

**Pitfall:** `check my_assertion {}` checks the empty constraint (always true), NOT the assertion. Omit the braces: `check my_assertion`.

---

## 4. Patterns and Idioms

### 4.1 Structural Patterns

**Reachability via transitive closure:**

```alloy
fun descendants [o : Object] : set Object { o.^(entries.object) }
fun allReachable : set Object { Root.*(entries.object) }
```

**Override pattern** (updating a relation for one key):

```alloy
entries' = entries ++ (d -> newEntries)
// Replaces d's entries, keeps all other directories' entries unchanged
```

**Total ordering without `util/ordering`** (avoids exact scope limitation):

```alloy
sig Node { next : lone Node }
one sig first, last in Node {}
fact ordering {
  no next.first
  no last.next
  Node - first in first.^next
}
```

### 4.2 Behavioral Modeling Template

The canonical structure for any behavioral Alloy model:

```alloy
// --- State ---
var sig uploaded in File {}
sig File { var shared : set Token }

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

**Stuttering is mandatory.** It ensures composability, extends finite behaviors to infinite traces, and prevents deadlocked states from making liveness properties vacuously true.

### 4.3 Protocol Patterns

**Ring topology:**

```alloy
sig Node { succ : one Node }
fact ring { all n : Node | Node in n.^succ }
```

**Messages as tuples (recommended when message structure is simple):**

```alloy
abstract sig Type {}
one sig Candidate, Elect extends Type {}

sig Node {
  succ : one Node,
  var inbox : Type -> Node    // each tuple IS a message
}
```

No message atoms needed. No scope issues. Order-of-magnitude faster analysis.

**Messages as signatures** (when messages have complex structure -- requires generator axioms):

```alloy
abstract sig Message { payload : one Node }
sig CandidateMsg, ElectedMsg extends Message {}

fact generator {
  all n : Node | some m : CandidateMsg | m.payload = n
}
fact unique {
  all disj m1, m2 : CandidateMsg | m1.payload != m2.payload
}
```

**Derived state via temporal functions** (eliminates frame conditions for computed properties):

```alloy
fun Elected : set Node {
  { n : Node | once (before (some (Elect -> n) & n.inbox)
                     and no (Elect -> n) & n.inbox) }
}
```

### 4.4 Verification Patterns

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
    (eventually always enabled[n]) implies (always eventually acts[n])
}
assert eventually_elected {
  fairness implies eventually (some Elected)
}
check eventually_elected for 4 but 20 steps
```

**Inductive invariant** (dramatically faster than unbounded temporal check):

```alloy
pred inv { shared.Token in uploaded - trashed }

// Refactor init and transitions as predicates for induction
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

### 4.5 Event Depiction Idiom

Makes events visible in the Analyzer's trace visualizer. No performance penalty -- derived functions are only computed during visualization unless referenced in formulas.

```alloy
enum Event { UploadEv, DeleteEv, StutterEv }

fun upload_happens : Event -> File {
  { e : UploadEv, f : File | upload[f] }
}
fun stutter_happens : set Event {
  { e : StutterEv | stutter }
}

fun events : set Event {
  stutter_happens + (upload_happens + delete_happens).File
}

// Simplify the transitions fact:
fact transitions { init and always some events }

// Check mutual exclusion of events:
check at_most_one { always lone events } for 3
```

### 4.6 Encoding Trace Scenarios

Use the `;` (sequence) operator to describe specific execution traces:

```alloy
run scenario {
  some f : File, t : Token {
    upload[f] ; share[f, t] ; download[t] ; delete[f] ; always stutter
  }
} for 1 File, 1 Token
```

Always constrain the tail of the trace (e.g., `always stutter`) to prevent unexpected continuations.

---

## 5. Common Pitfalls

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

Without stuttering, deadlocked states have no valid infinite continuations, making the specification vacuously true for properties about those states.

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

// RIGHT: add fairness as a premise
pred fairness { always eventually (some f : File | upload[f]) or no (File - uploaded) }
assert progress { fairness implies eventually some uploaded }
```

### Pitfall 5: Over-constrained facts

Too many constraints can make the specification unsatisfiable, causing all `check` commands to trivially pass.

```alloy
// Always validate satisfiability after modifying facts:
run sanity_check {} for 5
// If this finds no instance, the facts are contradictory.
```

### Pitfall 6: Insufficient scope

The default scope of 3 atoms and 10 steps may hide counterexamples.

```alloy
check no_partitions for 3        // no counterexample (false confidence)
check no_partitions for 5        // counterexample found!

// For liveness, increase step bound:
check liveness_prop for 4 but 30 steps
```

### Pitfall 7: Quantifying over mutable sets outside `always`

Top-level formulas are evaluated at state 0 only.

```alloy
// WRONG: Elected is likely empty at state 0
all n : Elected | some n.inbox

// RIGHT: place inside always so it re-evaluates each state
always (all n : Elected | some n.inbox)
```

---

## 6. Complete Example: Behavioral File Sharing

A model of a cloud file sharing app demonstrating mutable state, temporal logic, frame conditions, safety, liveness, event depiction, and scenarios.

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
  f not in uploaded
  uploaded' = uploaded + f
  trashed' = trashed
  shared' = shared
}

pred delete [f : File] {
  f in uploaded - trashed
  trashed' = trashed + f
  uploaded' = uploaded
  shared' = shared
}

pred restore [f : File] {
  f in trashed
  trashed' = trashed - f
  uploaded' = uploaded
  shared' = shared
}

pred share [f : File, t : Token] {
  f in uploaded - trashed
  historically t not in File.shared
  shared' = shared + f -> t
  uploaded' = uploaded
  trashed' = trashed
}

pred download [t : Token] {
  some shared.t & (uploaded - trashed)
  uploaded' = uploaded
  trashed' = trashed
  shared' = shared
}

pred empty {
  some trashed
  uploaded' = uploaded - trashed
  trashed' = trashed - trashed
  shared' = shared - trashed -> Token
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

// --- Safety ---

assert shared_are_accessible {
  always (shared.Token in uploaded - trashed)
}

assert trashed_are_uploaded {
  always (trashed in uploaded)
}

// --- Liveness ---

pred fairness_on_empty {
  (eventually always some trashed) implies (always eventually empty)
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

// --- Event depiction ---

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
} for 2 but 8 steps expect 0

check shared_are_accessible for 5 but 15 steps expect 0
check trashed_are_uploaded for 5 but 15 steps expect 0
check restore_undoes_delete for 4 but 10 steps expect 0
check trash_eventually_emptied for 3 but 20 steps
```

---

## 7. Analyzer Integration

### Detection

Check in this order:
1. `alloy` command on `PATH`
2. `org.alloytools.alloy.dist.jar` in the project or common locations
3. Any `alloy*.jar` file in the project directory

### Invocation

```bash
# CLI:
alloy exec <file.als>

# CLI with markdown file:
alloy exec <file.md>

# JAR:
java -jar <path-to-alloy.jar> <file.als>

# JAR with markdown file:
java -jar <path-to-alloy.jar> <file.md>
```

### Interpreting Output

| Output | Meaning |
|--------|---------|
| "Instance found" | `run` found a satisfying instance |
| "No instance found" | `run`: spec may be over-constrained |
| "Counterexample found" | `check`: assertion is violated |
| "No counterexample found" | `check`: assertion holds within scope |

Report results as **bounded analysis**: say no counterexample was found within the checked scope, not that the system is proved correct.

If the Analyzer is not available, produce the `.als` file and inform the user to open it in the Alloy Analyzer GUI or install the CLI tool.

---

## 8. File Conventions

- Extension: `.als`, one module per file
- `module` declaration should match the filename
- Comments: `//` (line), `--` (line), `/* ... */` (block)
- Place specifications in a `models/` or `alloy/` directory
- Organize with section comments: `// --- Signatures ---`, `// --- Events ---`, etc.
- When writing Alloy in a markdown file (`.md`), the file must start with a YAML header (frontmatter): three dashes on the first line, followed by a title field in YAML format, followed by three more dashes.
```yaml
---
title: [ModelTitle]
---
```

---

## 9. Final Checklist

Before delivering a model, verify:

- [ ] Is there a minimal satisfiable `run` command?
- [ ] Are facts assumptions rather than desired conclusions?
- [ ] Do all action predicates have complete frame conditions?
- [ ] Is `stutter` present if the model is behavioral?
- [ ] Are safety and liveness properties separated?
- [ ] Are fairness assumptions explicit and justified?
- [ ] Are scopes and step bounds written explicitly?
- [ ] Are results reported as bounded analysis rather than proof?
