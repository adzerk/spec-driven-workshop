---
name: tlaplus
description: Formally specify and verify system designs, state machines, algorithms, and concurrent protocols using TLA+ and PlusCal. Covers invariants, temporal properties, model checking with TLC, fairness, and reporting results responsibly.
license: MIT
compatibility: opencode
metadata:
  domain: formal-methods
  languages: tla-plus,pluscal
---

# TLA+ Specification Skill

Write TLA+ and PlusCal specs. Model check with TLC. Report findings honestly.

## What I Do

- Write TLA+ and PlusCal specifications for systems, algorithms, state machines, and protocols.
- Define safety properties (invariants) and liveness properties (temporal formulas).
- Model concurrency, nondeterminism, race conditions, and deadlocks.
- Suggest TLC model configurations (constants, invariants, properties, constraints).
- Interpret TLC output and report results with appropriate caveats.

## When to Use Me

- Formally specify or verify a system design, protocol, or algorithm.
- Model a state machine, concurrent system, or distributed protocol.
- Find race conditions, deadlocks, or invariant violations before writing code.
- Check that "for all possible inputs/interleavings, property X holds".
- User mentions TLA+, PlusCal, TLC, model checking, or formal verification.

## When NOT to Use Me

- Runtime testing or production code (use a test framework).
- Pure CRUD with no concurrency or complex state logic.
- Simple request-response APIs with no shared mutable state.
- Anything requiring floating-point arithmetic (TLA+ has no float type).

---

## Mandatory Rules

These rules govern how to behave when writing specs and reporting results.

1. **Never say "proved correct".** Say "no counterexample found within the checked bounds" and state the model configuration (constants, constraints, workers).
2. **Always surface modeling assumptions** you introduced to resolve ambiguity in the user's design. List them explicitly.
3. **Always state fairness assumptions.** If liveness is in scope, list every `WF_`/`SF_` used and why. If safety-only, say "no fairness (safety-only run)".
4. **Guard against vacuous success** before calling a run "pass":
   - Show that at least one non-stuttering transition is reachable.
   - If using `CONSTRAINT` or `ACTION_CONSTRAINT`, list each one and the behavior it excludes.
   - Reject properties that are tautological or trivially weakened.
   - If any vacuity check is inconclusive, report "inconclusive coverage" instead of "pass".
5. **Every action must fully specify all variables.** Use `UNCHANGED` for variables not modified.
6. **Every `CONSTANT` should have an `ASSUME`** documenting valid values.

---

## Workflow

1. **Clarify the system.** Identify state variables, agents/processes, and key properties.
2. **Choose PlusCal vs pure TLA+.**
   - PlusCal: sequential/concurrent processes with clear control flow.
   - Pure TLA+: state machines, complex fairness, refinement, or systems that don't map to sequential processes.
3. **Define constants and variables.** Parameterize for easy model-size adjustment.
4. **Write a `TypeInvariant` first.** Bounds-check every variable. Get this passing before adding correctness properties.
5. **Write actions / algorithm body.** One action per atomic step. If two things can't happen simultaneously in the real system, they are separate actions.
6. **Write safety invariants.** What must always be true?
7. **Write liveness properties (if needed).** What must eventually happen? Pair each with an explicit fairness assumption.
8. **Suggest a model configuration.** Small constants first (2-3 actors, 3-5 queue items). TLC checks all interleavings, so state space explodes combinatorially.
9. **Run TLC, interpret output, report per the reporting checklist below.**

---

## Quick Reference

### Syntax Essentials

```text
==          Definition (operators, invariants)
=           Comparison
:=          PlusCal assignment

/\          And (conjunction)
\/          Or (disjunction)
~           Not
=>          Implies
#           Not equals

\A x \in S : P(x)      For all
\E x \in S : P(x)      There exists

x'          Value of x in the next state
UNCHANGED x             x' = x
UNCHANGED <<x, y>>     Both unchanged

[]P         Always P
<>P         Eventually P
P ~> Q      P leads to Q
WF_v(A)     Weak fairness of action A over variables v
SF_v(A)     Strong fairness of action A over variables v
```

### Key Distinctions

```text
[x \in S |-> expr]      Function literal (maps each x to expr)
[S -> T]                Function set (all functions from S to T)
[key |-> val]           Record literal
[key: Set]              Record type set
[f EXCEPT ![k] = v]    Function update (copy with k remapped)
[f EXCEPT ![k] = @ + 1]  @ refers to old value of f[k]
```

### PlusCal Essentials

```text
variables     Global variable declarations
define        Pure TLA+ operators visible to invariants
macro         Textual substitution (no labels allowed inside)
procedure     Reusable block (can contain labels, requires EXTENDS Sequences)
process       Concurrent actor (fair = weak fairness, fair+ = strong fairness)
await         Guard: blocks until condition is true
either/or     Nondeterministic choice (TLC explores all branches)
with x \in S  Nondeterministic binding (blocks if S is empty)
```

Label rules:
- Algorithm must begin with a label.
- `while` must be preceded by a label.
- Each variable updated at most once per label (use `||` for simultaneous assignment).
- `goto` must be followed by a label.

### Common Pitfalls

| Mistake | Fix |
|---------|-----|
| `Op(x) = expr` | Use `==` for definitions: `Op(x) == expr` |
| `x == y` in a guard | Use `=` for comparison: `x = y` |
| `seq[0]` | Sequences are 1-indexed: `seq[1]` |
| `\E i, j \in S : i # j => P` | Use `/\` with `\E`: `\E i, j \in S : i # j /\ P` |
| Two `EXTENDS` lines | One line: `EXTENDS Integers, Sequences, TLC` |
| Missing `UNCHANGED` in a pure TLA+ action | Every action must specify ALL variables |
| Updating a variable twice in one label | Use `\|\|` for simultaneous assignment |
| Misaligned `/\` `\/` bullets | Indentation determines grouping; verify alignment |

---

## Templates

### Pure TLA+ State Machine

```tla
---- MODULE StateMachine ----
EXTENDS Integers

VARIABLE state

States == {"Off", "Starting", "Running", "Stopping"}

TypeInvariant == state \in States

Init == state = "Off"

Trans(from, to) ==
  /\ state = from
  /\ state' = to

Next ==
  \/ Trans("Off", "Starting")
  \/ Trans("Starting", "Running")
  \/ Trans("Running", "Stopping")
  \/ Trans("Stopping", "Off")

Spec == Init /\ [][Next]_state
====
```

### Concurrent Shared State (Pure TLA+)

```tla
---- MODULE SharedCounter ----
EXTENDS Integers
CONSTANTS Threads, NULL

ASSUME Threads # {}
ASSUME NULL \notin Threads

VARIABLES counter, lock

vars == <<counter, lock>>

TypeInvariant ==
  /\ counter \in Nat
  /\ lock \in Threads \union {NULL}

Init ==
  /\ counter = 0
  /\ lock = NULL

Acquire(t) ==
  /\ lock = NULL
  /\ lock' = t
  /\ UNCHANGED counter

Increment(t) ==
  /\ lock = t
  /\ counter' = counter + 1
  /\ UNCHANGED lock

Release(t) ==
  /\ lock = t
  /\ lock' = NULL
  /\ UNCHANGED counter

Next == \E t \in Threads :
  \/ Acquire(t)
  \/ Increment(t)
  \/ Release(t)

\* Safety only (no fairness)
Spec == Init /\ [][Next]_vars

\* With weak fairness for liveness
Fairness == \A t \in Threads :
  /\ WF_vars(Acquire(t))
  /\ WF_vars(Increment(t))
  /\ WF_vars(Release(t))

FairSpec == Spec /\ Fairness

MutualExclusion ==
  \A t1, t2 \in Threads :
    lock = t1 /\ lock = t2 => t1 = t2
====
```

### PlusCal Process with Fairness

```tla
---- MODULE ProducerConsumer ----
EXTENDS Integers, Sequences, TLC
CONSTANTS Producers, Consumers, BufSize, NULL

ASSUME Producers # {}
ASSUME Consumers # {}
ASSUME Producers \intersect Consumers = {}
ASSUME BufSize \in Nat /\ BufSize > 0

(* --algorithm prodcons
variables
  buf = <<>>;

define
  TypeInvariant ==
    /\ buf \in Seq(Producers)
    /\ Len(buf) <= BufSize

  BufBounded == Len(buf) <= BufSize
end define;

fair process producer \in Producers
begin
  Produce:
    while TRUE do
      await Len(buf) < BufSize;
      buf := Append(buf, self);
    end while;
end process;

fair process consumer \in Consumers
begin
  Consume:
    while TRUE do
      await buf # <<>>;
      buf := Tail(buf);
    end while;
end process;

end algorithm; *)
====
```

### TLC Configuration Skeleton

```text
SPECIFICATION Spec

CONSTANTS
    Threads = {t1, t2}
    NULL = NULL

\* Safety
INVARIANT TypeInvariant
INVARIANT MutualExclusion

\* Liveness (use FairSpec as SPECIFICATION when checking these)
\* PROPERTY Liveness

\* Bound state space
\* CONSTRAINT StateConstraint

\* Symmetry (safety only, not compatible with liveness)
\* SYMMETRY Permutations(Threads)
```

---

## Reporting Checklist

After a TLC run, report results using this structure:

1. **Specification**: which module and spec formula were checked.
2. **Model configuration**: constant assignments, number of workers.
3. **Properties checked**: list each invariant and temporal property.
4. **Constraints applied**: list each `CONSTRAINT` / `ACTION_CONSTRAINT` and what it bounds.
5. **Fairness**: which actions have `WF_`/`SF_`, or "none (safety-only run)".
6. **Result**: one of:
   - "No counterexample found" -- state the number of distinct states and the diameter.
   - "Invariant X violated" -- summarize the error trace.
   - "Deadlock reached" -- summarize the trace.
   - "Temporal property violated" -- summarize the lasso trace.
   - "Inconclusive coverage" -- if vacuity checks did not pass.
7. **Modeling assumptions**: list any assumptions you introduced that were not in the user's original design.
8. **Caveats**: state what is NOT covered (e.g., "checked with 2 threads; real system uses N", "network failures not modeled").

### Example Wording

> **Result**: No counterexample found for `MutualExclusion` and `TypeInvariant`.
> Model: `Threads = {t1, t2}`, `NULL = [model value]`, 4 workers.
> 847 distinct states, diameter 12. No fairness (safety-only run).
>
> **Assumptions introduced**: Lock acquisition is atomic (single step). Threads do not crash mid-critical-section.
>
> **Not covered**: Liveness (no fairness constraints in this run). More than 2 threads.

---

## Interpretation Guide

| TLC Output | Meaning | Typical Fix |
|------------|---------|-------------|
| "No errors found" + state count | Design holds for these constants | Scale up constants or add more properties |
| "Invariant X is violated" + trace | A reachable state breaks safety | Trace shows the exact interleaving; add synchronization, locks, or tighten guards |
| "Deadlock reached" | No action is enabled in some state | Add a missing transition, relax an `await`, or disable deadlock checking if expected |
| "Temporal property violated" + lasso | Liveness fails (system can loop forever without progress) | Add or strengthen fairness, fix starvation |
| State space explodes | Model is too large | Reduce constants, add symmetry, fuse labels, use `CONSTRAINT` |
