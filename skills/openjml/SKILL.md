---
name: openjml
description: >
  Write JML (Java Modeling Language) specifications for Java programs and verify them using OpenJML.
  Covers the full workflow: writing specs, running static verification (ESC) and runtime assertion
  checking (RAC), interpreting verification results, and debugging failures.
---

# JML Specification & OpenJML Verification

You are an expert at writing JML (Java Modeling Language) formal specifications for Java code and
verifying them with OpenJML. Follow these instructions precisely when the user asks you to write,
review, or debug JML specifications.

---

## 1. When To Use This Skill

Use this skill when the user asks you to:

- Write JML specifications (preconditions, postconditions, invariants, loop specs) for Java code
- Verify Java code with OpenJML (`--esc` or `--rac`)
- Debug or fix OpenJML verification failures
- Review existing JML specs for correctness or completeness
- Explain JML syntax or semantics

Do NOT use this skill for non-Java languages or for testing frameworks (JUnit, etc.) unless the
user explicitly wants JML-based verification alongside tests.

---

## 2. What Is JML

JML is a behavioral interface specification language for Java. Specifications are written as
specially-formatted Java comments (`//@` for single-line, `/*@ ... @*/` for multi-line) and
describe what methods must do, not how they do it. OpenJML is the tool that checks whether
Java code satisfies its JML specifications, using either:

- **ESC (Extended Static Checking):** Proves correctness at compile time via SMT solvers. No
  test inputs needed. This is the primary mode.
- **RAC (Runtime Assertion Checking):** Compiles specs into runtime checks, evaluated during
  execution. Useful when ESC times out or for integration testing.

---

## 3. JML Syntax Essentials

### Comment Markers

```java
//@ single-line JML annotation
/*@ multi-line
  @ JML annotation
  @*/
```

### Method-Level Clauses

| Clause | Meaning |
|---|---|
| `requires P;` | Precondition: caller must establish `P` before calling |
| `ensures Q;` | Postcondition: method must establish `Q` on normal return |
| `assignable x, y;` | Frame condition: only `x` and `y` may be modified (aliases: `assigns`, `modifies`, `writes`) |
| `signals (E e) R;` | If exception `E` is thrown, `R` holds |
| `signals_only E1, E2;` | Only these checked exceptions may be thrown |
| `also` | Separates multiple specification cases |
| `normal_behavior` | Spec case that implies `signals (Exception e) false;` |
| `exceptional_behavior` | Spec case that implies `ensures false;` |

### Class-Level Clauses

| Clause | Meaning |
|---|---|
| `invariant P;` | Must hold at entry and exit of every public method |
| `constraint P;` | History constraint: relates pre- and post-state |
| `initially P;` | Must hold after construction |

### Loop Specifications

Place these immediately before the loop statement:

| Clause | Meaning |
|---|---|
| `maintaining P;` (or `loop_invariant`) | Invariant preserved by each iteration |
| `loop_writes x;` (or `loop_modifies`, `loop_assignable`) | Variables modified by loop body |
| `decreases E;` | Integer expression that decreases each iteration and is non-negative; proves termination |

### Key Spec Expressions

| Expression | Meaning |
|---|---|
| `\result` | Return value of the method |
| `\old(expr)` | Value of `expr` at method entry |
| `\forall T x; range; body` | Universal quantification |
| `\exists T x; range; body` | Existential quantification |
| `\nothing` | Empty set (for `assignable`) |
| `\everything` | All memory (for `assignable`) |
| `P ==> Q` | Implication |
| `P <==> Q` | Bi-implication (if and only if) |
| `\count` | Loop iteration counter (inside `maintaining`) |
| `a[i..j]` | Array range (in quantified expressions, use explicit index) |

### Chained Relationals

JML supports chained comparisons. Prefer them for clarity:
```java
//@ requires 0 <= i && i < a.length;
// Better written as:
//@ requires 0 <= i < a.length;
```

### Visibility and Purity

- **`pure`**: Method has no side effects; can be used in specs. Implies `assignable \nothing;`.
- **`spec_pure`**: Like `pure` but verification only; no runtime check. Minimum required to use
  a method in a JML expression.
- **`strictly_pure`**: No side effects and does not read mutable state.
- **`no_state`**: Reads no state at all (only depends on parameters).
- **`nullable`**: Annotation on a parameter, return, or field to allow null. JML defaults
  everything to non-null.
- **`nullable_by_default`**: Class-level modifier to flip the default.

---

## 4. Specification Writing Process

Follow this exact sequence when writing JML specs for a method. Do not skip steps.

### Step 1: Identify Purpose and Contract Type

- Is this a **query** (returns a value, no side effects)? Use `pure` and focus on `ensures`.
- Is this a **command** (modifies state)? Focus on `assignable` + `ensures`.
- Can it throw exceptions? Plan `signals` / `signals_only` clauses.

### Step 2: Annotate Nullness

- JML defaults to `non_null` for all reference types.
- If any parameter, return, or field legitimately can be null, annotate it `/*@ nullable @*/`.
- If most things in the class are nullable, use `/*@ nullable_by_default @*/` on the class.

### Step 3: Write Preconditions (`requires`)

- State what must be true for the method to execute correctly.
- Order clauses so earlier ones establish well-definedness for later ones:
  ```java
  //@ requires a != null;          // enables safe dereference
  //@ requires 0 <= i < a.length;  // depends on a != null
  ```
- Multiple `requires` clauses are conjoined (AND).

### Step 4: Write Frame Conditions (`assignable`)

- List every field and array element that the method may modify.
- Use exactly ONE `assignable` clause per spec case. Multiple `assignable` clauses compute
  their **intersection**, not union -- this is a critical gotcha.
- If the method is pure, write `assignable \nothing;`.
- If you are unsure, start with `assignable \everything;` and narrow it down.
- For array elements: `assignable a[*];` or `assignable a[i..j];`.

### Step 5: Write Postconditions (`ensures`)

- Describe the return value with `\result`.
- Describe state changes using `\old(expr)` to reference pre-state values.
- Multiple `ensures` clauses are conjoined (AND).
- Make postconditions **strong enough** for callers to reason about the result (see Section 9).

### Step 6: Specify Exceptional Behavior

- Use `signals (ExceptionType e) condition;` to describe when/why exceptions are thrown.
- Use `signals_only ExceptionType;` to restrict which exceptions may escape.
- For methods that never throw: `signals (Exception e) false;` or use `normal_behavior`.

### Step 7: Write Loop Specifications (if applicable)

Every loop MUST have all three parts:
1. **`maintaining`**: The loop invariant. Must include:
   - Range of the loop variable including the exit value (e.g., `0 <= i <= a.length`, NOT `< a.length`)
   - The inductive property being established
2. **`loop_writes`**: Every variable or array element modified in the loop body
3. **`decreases`**: An integer expression that strictly decreases each iteration

### Step 8: Verify and Iterate

1. Run `openjml --esc FileName.java`
2. If verification fails, read the failure name and line number
3. Consult Section 7 (failure remediation table) and Section 8 (common pitfalls)
4. Fix the spec or code, then re-verify
5. Repeat until exit code 0

---

## 5. Spec Templates

Use these fill-in templates as starting points. Replace `{...}` placeholders.

### Pure Query Method

```java
/*@ public normal_behavior
  @   requires {precondition};
  @   ensures \result == {expression relating params/fields to return value};
  @*/
public /*@ pure @*/ {ReturnType} {methodName}({params}) {
    ...
}
```

### State-Modifying Method

```java
/*@ public normal_behavior
  @   requires {precondition};
  @   assignable {field1}, {field2};
  @   ensures {field1} == {new value} && {field2} == \old({field2}) + {delta};
  @*/
public void {methodName}({params}) {
    ...
}
```

### Method with Normal + Exceptional Behavior

```java
/*@ public normal_behavior
  @   requires {normal precondition};
  @   assignable {fields};
  @   ensures {postcondition};
  @ also
  @ public exceptional_behavior
  @   requires {exceptional precondition};
  @   assignable \nothing;
  @   signals_only {ExceptionType};
  @*/
public {ReturnType} {methodName}({params}) throws {ExceptionType} {
    ...
}
```

### Loop with Full Spec

```java
//@ maintaining 0 <= i <= a.length;
//@ maintaining {inductive property over a[0..i)};
//@ loop_writes i, {other modified vars};
//@ decreases a.length - i;
for (int i = 0; i < a.length; i++) {
    ...
}
```

### Class Invariant

```java
public class {ClassName} {
    private int {field};

    //@ invariant {field} >= 0;
    //@ invariant {other property};

    ...
}
```

---

## 6. Running OpenJML

### Extended Static Checking (Primary)

```bash
openjml --esc FileName.java
```

- Exit code **0**: All specifications verified.
- Exit code **6**: One or more verification failures. Read output for failure names and locations.
- Other exit codes: Compilation errors, parse errors, or tool issues.

### Useful ESC Flags

| Flag | Purpose |
|---|---|
| `--esc` | Run static verification |
| `--esc-max-warnings=N` | Limit number of warnings reported (default: all) |
| `--timeout=N` | Per-method timeout in seconds (default: varies) |
| `--method={name}` | Verify only the named method |
| `--prover=z3` | Select SMT solver (z3 is default) |
| `--show` | Show the logical assertions being checked |
| `--trace` | Show a counterexample trace on failure |
| `--progress` | Show progress during verification |
| `--sourcepath=dir` | Add source path for dependent files |
| `--specspath=dir` | Add path to spec files (.jml) |
| `--classpath=path` | Set classpath for dependencies |

### Runtime Assertion Checking

```bash
# Compile with RAC instrumentation:
openjml --rac FileName.java

# Run the instrumented class:
openjml --rac-run ClassName
```

RAC is useful when:
- ESC times out on complex methods
- You want to test specs against concrete inputs
- You want specs enforced during integration testing

### Verifying Multiple Files

```bash
openjml --esc src/*.java
openjml --esc --sourcepath=src src/Main.java
```

### Workflow Tip

Always verify incrementally: write specs for one method, verify, fix, then move to the next.
Do not spec an entire class and then verify -- failures cascade and become hard to diagnose.

---

## 7. Interpreting & Fixing Verification Failures

When OpenJML reports a failure, the output contains a failure name and source location. Use
this table to diagnose and fix the issue.

| Failure Name | Meaning | Likely Cause | Fix |
|---|---|---|---|
| `Postcondition` | `ensures` clause not satisfied at method exit | Postcondition too strong, or implementation does not establish it | Weaken the postcondition, or fix the implementation. Check that all code paths satisfy it. |
| `Precondition` | Caller does not establish `requires` of a called method | Missing precondition on the calling method, or calling without checking | Add `requires` to the caller that implies the callee's precondition. |
| `UndefinedCalledMethodPrecondition` | A method called in a spec expression has an unsatisfied precondition | Spec references a method whose own `requires` is not met | Ensure the spec context guarantees the called method's preconditions. |
| `Assignable` | Method modifies something not listed in `assignable` | Missing field/array in `assignable`, or called method has `assignable \everything` | Add the modified location to `assignable`. Check called methods' frame conditions. |
| `PossiblyNullDeReference` | Potential null dereference | JML's non-null default is violated, or missing null check | Add `requires x != null;` or annotate the reference `nullable` and add a null guard. |
| `UndefinedNullDeReference` | Null dereference inside a spec expression | Spec expression dereferences something possibly null | Reorder `requires` so non-null is established first, or add explicit null check in spec. |
| `LoopInvariantBeforeLoop` | Loop invariant does not hold on loop entry | Invariant is wrong at initial values | Check that the invariant holds for the initial value of the loop variable. |
| `LoopInvariant` | Loop invariant not preserved by loop body | Body breaks the invariant, or invariant is too weak | Strengthen the invariant to account for body's modifications, or fix the body. |
| `LoopDecreases` | `decreases` expression does not strictly decrease | Wrong variant expression, or loop modifies it unexpectedly | Fix the `decreases` expression to reflect the actual loop progress. |
| `InvariantEntrance` | Class invariant does not hold at method entry | Object was constructed/modified without establishing invariant | Check constructors and all methods that modify invariant-related fields. |
| `InvariantExit` | Class invariant does not hold at method exit | Method breaks the invariant | Ensure method re-establishes all class invariants before returning. |
| `ExceptionList` | Method throws an exception not in `signals_only` | Uncovered exception type | Add the exception to `signals_only`, or add a `signals` clause for it. |
| `ArithmeticOperationRange` | Arithmetic overflow/underflow | Integer computation may overflow | Add preconditions bounding inputs, or cast to `long` before computing. |
| `ArrayIndex` | Array index out of bounds | Index not proven within `[0, length)` | Add `requires` or `maintaining` that bounds the index. |
| `NegativeArraySize` | Array created with negative size | Size expression may be negative | Add `requires size >= 0;` or equivalent. |
| `Assert` | An explicit `//@ assert P;` failed | The assertion is not provable at that point | Strengthen preceding specs or add intermediate `assert` statements to help the prover. |

### Reading Counterexample Traces

Run with `--trace` to get a counterexample when verification fails:
```bash
openjml --esc --trace FileName.java
```

The trace shows concrete values that violate the spec. Use these to understand exactly which
execution path causes the failure.

---

## 8. Common Pitfalls

Each entry shows the mistake, why it fails, and the fix.

### Pitfall 1: Multiple `assignable` Clauses (Intersection, Not Union)

```java
// WRONG -- computes intersection, which is \nothing
//@ assignable x;
//@ assignable y;
// FIX -- single clause with both
//@ assignable x, y;
```

### Pitfall 2: Loop Invariant Excludes Exit Value

```java
// WRONG -- fails LoopInvariantBeforeLoop when i == a.length at exit
//@ maintaining 0 <= i < a.length;
// FIX -- include the exit value
//@ maintaining 0 <= i <= a.length;
```

### Pitfall 3: Missing Loop Spec Part

OpenJML requires all three loop spec clauses. Omitting any one causes failures or unsoundness.

```java
// WRONG -- missing loop_writes and decreases
//@ maintaining 0 <= i <= n;
for (int i = 0; i < n; i++) { ... }

// FIX -- all three parts
//@ maintaining 0 <= i <= n;
//@ loop_writes i, otherVars;
//@ decreases n - i;
for (int i = 0; i < n; i++) { ... }
```

### Pitfall 4: `requires` Ordering Breaks Well-Definedness

```java
// WRONG -- a[i] dereference may be undefined if a is null
//@ requires 0 <= i < a.length;
//@ requires a != null;

// FIX -- null check first
//@ requires a != null;
//@ requires 0 <= i < a.length;
```

### Pitfall 5: Forgetting `assignable` Defaults to `\everything`

If you omit `assignable`, OpenJML assumes `assignable \everything`, which means every field
and array is considered modified. This destroys all knowledge about unchanged state after the
method call, causing downstream verification failures.

```java
// WRONG -- no assignable means \everything
//@ ensures \result == x + 1;
public int increment(int x) { return x + 1; }

// FIX -- declare it pure or add assignable \nothing
//@ ensures \result == x + 1;
//@ assignable \nothing;
public int increment(int x) { return x + 1; }
// Or better:
//@ ensures \result == x + 1;
public /*@ pure @*/ int increment(int x) { return x + 1; }
```

### Pitfall 6: Using Impure Methods in Spec Expressions

```java
// WRONG -- size() is not declared pure, so it cannot appear in a spec
//@ ensures \result == size() - 1;

// FIX -- declare size() as pure
//@ ensures \result >= 0;
public /*@ pure @*/ int size() { return count; }
// Now this works:
//@ ensures \result == size() - 1;
```

### Pitfall 7: Forgetting JML Non-Null Default

```java
// WRONG -- JML assumes s is non-null, but caller might pass null
public int length(String s) {
    if (s == null) return 0;
    return s.length();
}

// FIX option A -- add nullable annotation
public int length(/*@ nullable @*/ String s) { ... }

// FIX option B -- add requires (if null is truly forbidden)
//@ requires s != null;
public int length(String s) { ... }
```

### Pitfall 8: Called Method's Frame Clobbers Caller's State

```java
// Called method has no assignable clause (defaults to \everything)
public void helper() { ... }

// Caller's spec fails because helper() may modify anything
//@ ensures field == \old(field) + 1;
public void caller() {
    field++;
    helper(); // OpenJML assumes helper() may have changed field
}

// FIX -- add assignable to helper()
//@ assignable \nothing;
public void helper() { ... }
```

### Pitfall 9: `normal_behavior` with Possible Exceptions

```java
// WRONG -- normal_behavior implies signals (Exception) false,
// but the body may throw ArrayIndexOutOfBoundsException
/*@ public normal_behavior
  @   ensures \result == a[i];
  @*/
public int get(int[] a, int i) { return a[i]; }

// FIX -- add requires to prevent the exception
/*@ public normal_behavior
  @   requires a != null;
  @   requires 0 <= i < a.length;
  @   ensures \result == a[i];
  @*/
public int get(int[] a, int i) { return a[i]; }
```

---

## 9. Specification Strength & Completeness

Weak specifications verify easily but provide little value to callers. Strong specifications
enable modular verification -- callers can reason about the method's effect without reading
its implementation.

### Guidelines

1. **Postconditions should fully characterize the result.** For a `find` method, don't just say
   `\result >= -1`. Say:
   ```java
   //@ ensures \result == -1 ==> (\forall int j; 0 <= j < a.length; a[j] != key);
   //@ ensures \result >= 0 ==> (0 <= \result < a.length && a[\result] == key);
   ```

2. **Frame conditions should be as tight as possible.** `assignable \everything` is almost
   never correct. List exactly what changes.

3. **Preconditions should be as weak as possible.** Don't require more than the method actually
   needs. Overly strong preconditions reduce the method's usability.

4. **Invariants should capture the class's representation property.** For example, a sorted
   array class should have:
   ```java
   //@ invariant (\forall int i; 0 <= i < size - 1; data[i] <= data[i + 1]);
   ```

5. **Every public method should have a spec.** Private/package methods need specs when called
   from a specified public method, because OpenJML verifies modularly -- it uses the callee's
   spec, not its body.

6. **Use `assert` to help the prover.** If ESC fails on a complex method, insert intermediate
   `//@ assert P;` statements to break the proof into smaller steps.

7. **Ghost variables for abstract state.** When the concrete representation is complex, introduce
   ghost fields to represent abstract state and specify in terms of those:
   ```java
   //@ ghost public int abstractSize;
   //@ invariant abstractSize == computeSize();
   ```

---

## 10. Further Reference

- **OpenJML Tutorial:** https://www.openjml.org/tutorial/ -- Worked examples for all major
  features, from basic postconditions through ghost variables.
- **JML Reference Manual:** https://www.openjml.org/documentation/JML_Reference_Manual.pdf --
  Complete syntax and semantics for all JML constructs.
- **OpenJML User Guide:** https://www.openjml.org/documentation/OpenJMLUserGuide.pdf --
  Tool options, configuration, and integration with build systems.
- **OpenJML GitHub:** https://github.com/OpenJML/OpenJML -- Source, issues, and release builds.
