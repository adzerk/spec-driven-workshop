---
name: openjml-more
description: >
  Write JML (Java Modeling Language) specifications for Java programs and verify them using OpenJML.
  Covers the full workflow: writing specs, running static verification (ESC) and runtime assertion
  checking (RAC), interpreting verification results, and debugging failures. (more details)
---

# JML Specification & OpenJML Verification

This is expert guidance when using JML (Java Modeling Language) formal specifications for Java code and
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

**Code formatter compatibility:** Java code formatters (Palantir Java Format, Google Java
Format, etc.) will silently convert `//@` to `// @` by inserting a space after `//`. The
result is a plain Java comment that OpenJML completely ignores -- no warning is emitted, and
the annotation simply has no effect. This silently disables `skipesc`, `assume`, `assert`,
invariants, and any other single-line JML annotation.

**Always prefer `/*@ ... @*/` block-comment style** when the project uses an automated code
formatter. Block comments are not reformatted by standard Java formatters. This applies to
all JML annotations, including short ones:

```java
// WRONG -- formatter will break this into "// @ skipesc"
//@ skipesc

// SAFE -- block comment survives formatting
/*@ skipesc @*/

// SAFE -- inline modifiers in block comments also survive
private /*@ spec_public @*/ int count;
public /*@ pure @*/ int size() { return count; }
```

If you must use single-line style, configure the formatter to exclude JML comments (e.g.,
Spotless `toggleOffOn` tags) or verify that annotations survive after running the formatter.

### Method-Level Clauses

| Clause | Meaning |
|---|---|
| `requires P;` | Precondition: caller must establish `P` before calling |
| `ensures Q;` | Postcondition: method must establish `Q` on normal return |
| `assignable x, y;` | Frame condition: only `x` and `y` may be modified (aliases: `assigns`, `modifies`, `writes`) |
| `signals (E e) R;` | If exception `E` is thrown, `R` holds |
| `signals_only E1, E2;` | Only these exception types (checked or unchecked) may be thrown |
| `also` | Separates multiple specification cases; also joins inherited specs when overriding a method (see Section 13) |
| `normal_behavior` | Spec case that implies `signals (Exception e) false;` |
| `exceptional_behavior` | Spec case that implies `ensures false;` |

### Class-Level Clauses

| Clause | Meaning |
|---|---|
| `invariant P;` | Must hold in every visible state: at the end of construction, and at the entry and exit of every non-helper public method (including inherited methods). See JML's visible-state semantics. |
| `constraint P;` | History constraint: relates pre- and post-state across method calls |
| `initially P;` | Must hold at the end of every constructor |
| `static_initializer` | Specifies the `static { }` initializer block. Must be placed immediately before it. Supports `ensures` clauses for static field initialization. |

Example of `static_initializer`:
```java
public static final Comparator<Event<?>> ORDERING;

/*@ public static_initializer
  @   ensures ORDERING != null;
  @*/
static {
    ORDERING = Comparator.comparingLong(Event::time)
                         .thenComparingLong(Event::sequence);
}
```

Note: Lambdas inside static initializer blocks can trigger OpenJML internal errors ("NULL
SYMBOL") when verified together with other files. If this occurs, add `/*@ skipesc @*/`
before the static block.

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
| `\count` | Loop iteration counter (usable in any loop spec clause: `maintaining`, `decreases`, `loop_writes`) |

**Store-ref syntax for frame conditions:**

| Syntax | Context |
|---|---|
| `a[i]` | Single array element (in `assignable` / `loop_writes`) |
| `a[*]` | All elements of array `a` |
| `a[i..j]` | Array elements from index `i` to `j` (store-ref syntax for `assignable` / `loop_writes` only; not a general spec expression) |

### Chained Relationals

JML supports chained comparisons. Prefer them for clarity:
```java
//@ requires 0 <= i && i < a.length;
// Better written as:
//@ requires 0 <= i < a.length;
```

### Visibility and Purity

- **`pure`**: Method has no side effects; can be used in spec expressions. Implies
  `assignable \nothing;`. This is the standard modifier required to call a method inside a
  JML annotation.
- **`spec_pure`**: Like `pure` but applies only during verification (no RAC runtime check).
  Still requires the method to be side-effect-free for specification purposes. Use `pure`
  unless you specifically need to suppress the RAC instrumentation.
- **`strictly_pure`**: No side effects and does not read mutable state.
- **`no_state`**: Reads no state at all (only depends on parameters).
- **`nullable`**: Annotation on a parameter, return, or field to allow null. JML defaults
  reference types in declarations to non-null. (Array element nullity depends on the element
  type declaration; see Section 4 for visibility implications.)
- **`nullable_by_default`**: Class-level modifier to flip the default for all declarations
  in the class.
- **`helper`**: Exempts a constructor or method from class invariant checking at entry and
  exit. Normally, invariants must hold at the end of every constructor and at the entry/exit
  of every public method. Marking a method `helper` removes this obligation, which is useful
  when:
  - OpenJML cannot prove field assignments establish invariants in a constructor (a known
    prover limitation with some constructor forms).
  - An internal method is called in a state where invariants are temporarily broken.

  When using `helper`, try these approaches **in order** before resorting to `skipesc`:

  1. **First try: verify without `skipesc`.** Write the constructor spec and run ESC. OpenJML
     can prove constructor invariants for many simple field assignments.
  2. **Then try: add intermediate `//@ assert` statements** inside the constructor body to
     guide the prover through field-by-field invariant establishment.
  3. **Then try: use targeted `//@ assume` statements** for the specific invariant clause the
     prover struggles with. Document the reason (e.g., "OpenJML limitation: cannot prove field
     assignment establishes invariant through generic type parameter").
  4. **Last resort: apply `skipesc`.** Ensure the constructor still has a full JML contract
     (`requires`, `ensures` for each field assignment) so the factory method and callers can
     reason about it modularly.

  See Section 16 (Verification Escalation) for the full decision process.

  The standard pattern when `skipesc` is unavoidable is to mark the constructor `helper` and
  provide a public factory method that calls it. The factory method uses the constructor's
  postconditions to prove the invariants hold on the returned object:

  ```java
  /*@ requires initialState != null;
    @ ensures field == initialState;
    @*/
  /*@ skipesc @*/  // last resort -- only after assert/assume alternatives fail
  private /*@ helper @*/ MyClass(S initialState) {
      this.field = initialState;  // OpenJML may fail to prove this establishes invariants
  }

  /*@ public behavior
    @   requires initialState != null;
    @   ensures \result != null;
    @*/
  public static <S> MyClass<S> create(S initialState) {
      return new MyClass<>(initialState);  // invariants proven from constructor's spec
  }
  ```

---

## 4. Visibility Rules

JML enforces Java-like visibility on specifications. A specification clause can only reference
identifiers that are at least as visible as the method or class being specified.

### The Core Rule

- A `public` method's spec can only reference `public` fields, `public pure` methods, and
  `public` model fields.
- A `protected` method's spec can reference `protected` or `public` members.
- Package-private and `private` follow analogously.

### Problem: Public API, Private Representation

You often need to specify a public method in terms of private fields. Three solutions:

**1. `spec_public` / `spec_protected` (quick fix):**
```java
private /*@ spec_public @*/ int count;

//@ ensures \result == count;
public /*@ pure @*/ int size() { return count; }
```
This exposes the private field to public specs. Use sparingly -- it leaks implementation details.

**2. Model fields (preferred for abstraction):**
```java
//@ public model int size;
//@ private represents size = count;

//@ ensures \result == size;
public /*@ pure @*/ int size() { return count; }
```
See Section 5 for full details on model fields.

**3. Public pure methods as abstraction:**

Specify in terms of other public pure methods rather than fields:
```java
//@ ensures \result >= 0;
public /*@ pure @*/ int size() { return count; }

//@ ensures size() == \old(size()) + 1;
public void add(Object o) { ... }
```

### Common Violation

```java
private int[] data;

// WRONG -- public spec references private field
//@ ensures \result == data[i];
public int get(int i) { return data[i]; }

// FIX -- use spec_public or a model field
private /*@ spec_public @*/ int[] data;
```

OpenJML reports visibility violations as compile errors, not verification failures.

---

## 5. Model Fields, Ghost Fields, and `represents`

JML provides two kinds of specification-only fields for abstracting over implementation details.

### Model Fields

A **model field** exists only in the specification. It has no runtime storage. You define it
with `model` and connect it to the implementation with a `represents` clause:

```java
public class Stack {
    private Object[] elems;
    private int top;

    //@ public model int size;
    //@ private represents size = top;

    //@ public model boolean isEmpty;
    //@ private represents isEmpty = (top == 0);

    //@ public invariant size >= 0;

    //@ ensures size == \old(size) + 1;
    //@ assignable size;
    public void push(Object o) { elems[top++] = o; }
}
```

Key points:
- Model fields are declared with a visibility (`public model int size;`).
- The `represents` clause maps the model field to a concrete expression.
- The `represents` clause must be at least as visible as the fields it reads (typically
  `private represents`).
- Use model fields in `assignable` clauses to abstract frame conditions.
- OpenJML checks that the `represents` mapping is consistent with the spec.

### Ghost Fields

A **ghost field** is a specification-only variable that you assign to explicitly in ghost
statements. Unlike model fields, ghost fields have no `represents` clause -- you manage
their value directly:

```java
public class Counter {
    //@ ghost public int specCount = 0;

    //@ assignable specCount;
    //@ ensures specCount == \old(specCount) + 1;
    public void increment() {
        count++;
        //@ set specCount = specCount + 1;
    }
}
```

### When to Use Which

| Use case | Mechanism |
|---|---|
| Abstract a concrete field for public specs | Model field + `represents` |
| Track specification-only state (e.g., call count, protocol state) | Ghost field + `set` |
| Hide representation from callers | Model field |
| Need explicit control over value in proof | Ghost field |

---

## 6. Specification Writing Process

Follow this exact sequence when writing JML specs for a method. Do not skip steps.

### Step 1: Identify Purpose and Contract Type

- Is this a **query** (returns a value, no side effects)? Use `pure` and focus on `ensures`.
- Is this a **command** (modifies state)? Focus on `assignable` + `ensures`.
- Can it throw exceptions? Plan `signals` / `signals_only` clauses.

**Choosing `behavior` vs `normal_behavior`:** If the method body contains **any** `throw`
statement -- even in guard clauses like `Objects.requireNonNull()`, `if (x < 0) throw ...`,
or validation checks -- use `behavior`, not `normal_behavior`. This is the most common case
for public API methods that validate their inputs.

`normal_behavior` implies `signals (Exception e) false;`, so OpenJML will report a
verification failure on any method that can throw, even if the throw is unreachable given the
preconditions. `behavior` is always safe; `normal_behavior` is an optimization that adds an
implicit no-exceptions guarantee. Only use `normal_behavior` when you are certain the method
cannot throw under any circumstance (pure getters, simple computations with tight
preconditions, etc.).

### Step 2: Annotate Nullness

- JML defaults to `non_null` for reference types in declarations.
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
- For abstract state: you can list model fields in `assignable` (see Section 5).

### Step 5: Write Postconditions (`ensures`)

- Describe the return value with `\result`.
- Describe state changes using `\old(expr)` to reference pre-state values.
- Multiple `ensures` clauses are conjoined (AND).
- Make postconditions **strong enough** for callers to reason about the result (see Section 14).

### Step 6: Specify Exceptional Behavior

- Use `signals (ExceptionType e) condition;` to describe when/why exceptions are thrown.
- Use `signals_only ExceptionType;` to restrict which exceptions may escape (checked and
  unchecked).
- For methods that never throw: `signals (Exception e) false;` or use `normal_behavior`.

### Step 7: Write Loop Specifications (if applicable)

In practice, OpenJML needs all three loop spec clauses to verify a loop successfully. Omitting
any one typically causes verification failures or unsound results:

1. **`maintaining`**: The loop invariant. Must include:
   - Range of the loop variable including the exit value (e.g., `0 <= i <= a.length`, NOT `< a.length`)
   - The inductive property being established
2. **`loop_writes`**: Every variable or array element modified in the loop body
3. **`decreases`**: An integer expression that strictly decreases each iteration

### Step 8: Verify and Iterate

1. Run `openjml --esc FileName.java`
2. If verification fails, read the failure name and line number
3. Consult Section 11 (failure remediation table) and Section 12 (common pitfalls)
4. Fix the spec or code, then re-verify
5. Repeat until exit code 0

---

## 7. Spec Templates

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
/*@ maintaining 0 <= i <= a.length; @*/
/*@ maintaining {inductive property over a[0..i)}; @*/
/*@ loop_writes i, {other modified vars}; @*/
/*@ decreases a.length - i; @*/
for (int i = 0; i < a.length; i++) {
    ...
}
```

### Class Invariant

```java
public class {ClassName} {
    private int {field};

    /*@ invariant {field} >= 0; @*/
    /*@ invariant {other property}; @*/

    ...
}
```

---

## 8. Running OpenJML

### Extended Static Checking (Primary)

```bash
openjml --esc FileName.java
```

- Exit code **0**: All specifications verified.
- Exit code **1**: Parse errors, type errors, or JML syntax errors. The file did not compile.
  Read the compiler diagnostics to fix these before attempting verification.
- Exit code **6**: One or more verification failures. Read output for failure names and locations.
- Other exit codes: Tool issues or configuration problems.

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
| `--subexpressions` | Show values of subexpressions in counterexample traces (use with `--trace`) |
| `--counterexample` / `-ce` | Request a counterexample from the solver (more detail than `--trace` alone) |
| `--check-feasibility=all` | Check that each spec is satisfiable (detects vacuously true specs) |
| `--progress` | Show progress during verification |
| `--sourcepath=dir` | Add source path for dependent files |
| `--specspath=dir` | Add path to spec files (`.jml`); see Section 9 |
| `--classpath=path` | Set classpath for dependencies |
| `--code-math=MODE` | Arithmetic mode for code (see Section 10) |
| `--spec-math=MODE` | Arithmetic mode for specs (see Section 10) |

### Debugging Aids in Specs

In addition to command-line flags, JML provides in-spec debugging:

```java
//@ show expr;   // prints the value of expr during RAC, shows value in ESC traces
//@ assert P;    // intermediate assertion to narrow down where proofs fail
```

### Runtime Assertion Checking

RAC compiles JML specifications into runtime checks embedded in the bytecode, then executes
the program. It is a two-step process:

**Step 1 -- Compile with RAC instrumentation:**
```bash
openjml --rac FileName.java
```
This produces `.class` files with embedded assertion checks. Any JML annotation that cannot
be compiled (e.g., quantifiers over infinite domains) is silently skipped.

**Step 2 -- Run the instrumented class:**
```bash
openjml --rac-run ClassName
```
This executes the class and reports any JML assertion violations at runtime, with the source
location and failing clause.

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

## 9. Specification Files (`.jml`)

JML specifications can be placed in separate `.jml` files instead of (or in addition to)
inline annotations in `.java` files.

### When to Use `.jml` Files

- Specifying library code you cannot modify
- Keeping specs separate from implementation for organizational reasons
- Providing specs for third-party APIs

### File Structure

A `.jml` file mirrors the `.java` file's structure but contains only declarations and JML
annotations. Method bodies are replaced with `;`:

```java
// File: Stack.jml
public class Stack {

    //@ public model int size;

    //@ ensures size == \old(size) + 1;
    //@ assignable size;
    public void push(Object o);

    //@ ensures \result == size;
    public /*@ pure @*/ int getSize();
}
```

### Search Order

OpenJML searches for specs in this order:
1. `.jml` file on the specs path (`--specspath`)
2. `.jml` file on the source path (`--sourcepath`)
3. Inline annotations in the `.java` file

**Critical rule:** If a `.jml` file is found, its specifications **completely replace** any
inline JML annotations in the corresponding `.java` file. The inline annotations are silently
ignored. This is the most common source of confusion when using `.jml` files -- if your inline
specs seem to have no effect, check whether a `.jml` file exists on the search path.

### Specifying the Specs Path

```bash
openjml --esc --specspath=specs src/Main.java
```

---

## 10. Arithmetic Modes

Integer arithmetic in Java can overflow silently. JML and OpenJML provide three arithmetic
modes that control how overflow is handled during verification.

### The Three Modes

| Mode | Behavior |
|---|---|
| `java_math` | Java semantics: arithmetic wraps on overflow (mod 2^32 / 2^64). No overflow warnings. |
| `safe_math` | Java semantics, but OpenJML **warns** if an operation might overflow. |
| `bigint_math` | Mathematical integers: no overflow possible. All `int`/`long` values are treated as unbounded. |

### Defaults

- **Code** defaults to `safe_math`: Java wrap semantics, but overflow generates a verification
  warning (`ArithmeticOperationRange`).
- **Spec expressions** default to `bigint_math`: specs use true mathematical integers, so
  `\old(x) + 1` never overflows in a spec context.

### Setting Modes

**Per-file or per-class (JML annotation):**
```java
//@ code_java_math
//@ code_safe_math
//@ code_bigint_math
//@ spec_java_math
//@ spec_safe_math
//@ spec_bigint_math
```

**Command-line (applies globally):**
```bash
openjml --esc --code-math=java FileName.java    # suppress overflow warnings
openjml --esc --code-math=safe FileName.java     # warn on overflow (default)
openjml --esc --code-math=bigint FileName.java   # treat code as unbounded math
```

### Practical Guidance

- If you get `ArithmeticOperationRange` warnings and the overflow is intentional (e.g., hash
  functions), use `//@ code_java_math` on that method or pass `--code-math=java`.
- If you want to prove absence of overflow, keep the default `safe_math` and add preconditions
  that bound inputs tightly enough to prevent overflow.
- Spec expressions rarely need mode changes since `bigint_math` is the natural choice for
  mathematical reasoning.

---

## 11. Interpreting & Fixing Verification Failures

When OpenJML reports a failure, the output contains a failure name and source location. Use
this table to diagnose and fix the issue.

| Failure Name | Meaning | Likely Cause | Fix |
|---|---|---|---|
| `Postcondition` | `ensures` clause not satisfied at method exit | Postcondition too strong, or implementation does not establish it | Weaken the postcondition, or fix the implementation. Check that all code paths satisfy it. |
| `Precondition` | Caller does not establish `requires` of a called method | Missing precondition on the calling method, or calling without checking | Add `requires` to the caller that implies the callee's precondition. |
| `UndefinedCalledMethodPrecondition` | A method called in a spec expression has an unsatisfied precondition | Spec references a method whose own `requires` is not met | Ensure the spec context guarantees the called method's preconditions. |
| `Assignable` | Method modifies something not listed in `assignable` | Missing field/array in `assignable`, or called method has `assignable \everything` | Add the modified location to `assignable`. Check called methods' frame conditions. |
| `PossiblyNullDeReference` | Potential null dereference | JML's non-null default is violated, or missing null check | Add `requires x != null;` or annotate the reference `nullable` and add a null guard. |
| `PossiblyNullAssignment` | Assigning a possibly-null value to a non-null location | A nullable expression is assigned to a field, parameter, or return declared non-null | Add a null check before assignment, or make the target `nullable`, or prove the value is non-null via preconditions. |
| `PossiblyNullReturn` | Method may return null but return type is non-null | Implementation has a code path returning null, or a called method's return is nullable | Add `requires` to prevent the null path, annotate return as `nullable`, or fix the implementation. |
| `UndefinedNullDeReference` | Null dereference inside a spec expression | Spec expression dereferences something possibly null | Reorder `requires` so non-null is established first, or add explicit null check in spec. |
| `LoopInvariantBeforeLoop` | Loop invariant does not hold on loop entry | Invariant is wrong at initial values | Check that the invariant holds for the initial value of the loop variable. |
| `LoopInvariant` | Loop invariant not preserved by loop body | Body breaks the invariant, or invariant is too weak | Strengthen the invariant to account for body's modifications, or fix the body. |
| `LoopDecreases` | `decreases` expression does not strictly decrease | Wrong variant expression, or loop modifies it unexpectedly | Fix the `decreases` expression to reflect the actual loop progress. |
| `LoopDecreasesNonNegative` | `decreases` expression is negative at some point during the loop | The variant function goes below zero before the loop exits | Tighten the loop invariant to ensure the decreases expression stays non-negative, or fix the variant. |
| `InvariantEntrance` | Class invariant does not hold at method entry | Object was constructed/modified without establishing invariant | Check constructors and all methods that modify invariant-related fields. |
| `InvariantExit` | Class invariant does not hold at method exit | Method breaks the invariant | Ensure method re-establishes all class invariants before returning. |
| `Constraint` | History constraint violated | A `constraint` clause relating pre- and post-state is not satisfied after a method call | Fix the implementation to maintain the constraint, or weaken the constraint. |
| `Initially` | `initially` clause not established | Constructor does not establish the `initially` condition | Ensure all constructors establish the required initial condition. |
| `ExceptionList` | Method throws an exception not in `signals_only` | Uncovered exception type | Add the exception to `signals_only`, or add a `signals` clause for it. |
| `ArithmeticOperationRange` | Arithmetic overflow/underflow | Integer computation may overflow under `safe_math` (the default) | Add preconditions bounding inputs, cast to `long` before computing, or set `//@ code_java_math` if overflow is intentional. See Section 10. |
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

For richer output, combine flags:
```bash
openjml --esc --trace --subexpressions -ce FileName.java
```

This shows counterexample values for every subexpression, making it easier to pinpoint which
part of a complex spec or computation is failing.

### Detecting Vacuous Specs

A spec can verify trivially if its precondition is unsatisfiable (no valid inputs exist). Use
feasibility checking to detect this:

```bash
openjml --esc --check-feasibility=all FileName.java
```

This reports a warning for any method whose precondition is infeasible, meaning the spec is
vacuously true and provides no real guarantee.

---

## 12. Common Pitfalls

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

OpenJML needs all three loop spec clauses to verify successfully. Omitting any one causes
failures or allows unsound reasoning.

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

### Pitfall 10: Visibility Mismatch in Specs

```java
// WRONG -- public spec references private field
private int count;
//@ ensures \result == count;
public /*@ pure @*/ int size() { return count; }

// FIX -- use spec_public
private /*@ spec_public @*/ int count;
// Or use a model field (see Section 5)
```

---

## 13. Specification Inheritance

When a class overrides a method from a parent class or interface, JML specs are inherited and
combined automatically.

### How It Works

- The overriding method inherits all specification cases from the parent.
- If the overriding method adds its own specs, they are joined with the parent's specs using
  `also` semantics.
- **Preconditions** are **weakened** (disjoined): the overriding method must accept at least
  the same inputs as the parent.
- **Postconditions** are **strengthened** (conjoined): the overriding method must satisfy both
  its own and the parent's postconditions (for the relevant spec case).
- This enforces **behavioral subtyping** (Liskov Substitution Principle).

### Example

```java
public class Shape {
    //@ requires scale > 0;
    //@ ensures \result >= 0;
    public /*@ pure @*/ double area(double scale) { ... }
}

public class Circle extends Shape {
    /*@ also
      @   requires scale > 0;
      @   ensures \result == Math.PI * radius * radius * scale;
      @*/
    public /*@ pure @*/ double area(double scale) { ... }
}
```

The `also` keyword is required when the overriding method adds specification cases. OpenJML
verifies that `Circle.area` satisfies both Shape's postcondition (`\result >= 0`) and its
own (`\result == Math.PI * radius * radius * scale`).

### Key Points

- You do NOT need to repeat the parent's spec in the child -- it is inherited automatically.
- Use `also` at the beginning of the child's spec to add additional cases.
- If you omit `also`, OpenJML treats the child's spec as standalone, which may silently lose
  the parent's contract. Always use `also` when overriding a specified method.
- Interface method specs are inherited the same way.

---

## 14. Specification Strength & Completeness

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
   never correct. List exactly what changes. Use model fields in `assignable` to abstract
   frame conditions (see Section 5).

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

7. **Use model fields for abstract state.** When the concrete representation is complex,
   introduce model fields (Section 5) to represent abstract state. This keeps public specs
   independent of the implementation and supports clean specification inheritance:
   ```java
   //@ public model int size;
   //@ private represents size = elementCount;
   //@ public invariant size >= 0;
   ```
   Ghost fields are an alternative when you need explicit control over value assignments
   (e.g., tracking protocol state), but model fields are preferred for representation
   abstraction.

---

## 15. Limitations

OpenJML is a powerful tool but has known limitations. Being aware of these avoids wasted
debugging time.

- **Generics:** Partially supported. Complex generic types, wildcards, and bounded type
  parameters may cause verification failures or tool errors.
- **Enums:** Basic enum usage works, but complex enum patterns (abstract methods on enum
  constants, enum with generics) may not verify.
- **Varargs:** Limited support. Methods with varargs (`T... args`) may not verify correctly.
- **Concurrency:** JML and OpenJML do not support concurrent reasoning. Specs assume
  single-threaded execution.
- **Library specifications:** OpenJML ships specs for parts of `java.lang` and `java.util`,
  but coverage is incomplete. Missing library specs cause `UndefinedCalledMethodPrecondition`
  or weak reasoning. You may need to provide your own specs via `.jml` files (Section 9).
- **Lambda expressions and streams:** Limited or no support. Avoid using lambdas in code
  that needs verification, or isolate them behind specified wrapper methods.
- **Records:** Java records have several OpenJML quirks:
  - Record accessor methods (e.g., `event.time()`) may not resolve correctly in invariant
    or ensures clauses. Prefer direct field references where possible.
  - Record constructors work best with `public behavior` rather than `normal_behavior`, since
    the compact constructor form has implicit field assignments that OpenJML may not fully model.
  - Constructor `assignable` clauses on records cannot reference `this` (implicit or
    explicit) -- OpenJML rejects them with parse errors. Omit `assignable` from record
    constructor specs.
- **`PriorityQueue` and collections with incomplete specs:** `PriorityQueue.add()` has
  `assignable values` in its library spec, but the prover may treat it as modifying
  everything, destroying all field knowledge in the caller. `PriorityQueue.peek()` references
  internal model methods (`_get(0)`) with preconditions that cannot be proven externally.
  `PriorityQueue.remove()`, `queue.isEmpty()`, `ArrayList.add()`, `List.copyOf()`, and
  iterator-based `for-each` loops all have similar spec gaps. When a method uses these
  collection operations, try these approaches **in order**:
  1. **Use ghost/model fields or `//@ assume` after the collection call** to re-assert facts the prover lost.
     For example, after `queue.add(x)`, add `//@ assume this.otherField == \old(this.otherField);`
     for fields the collection call could not have modified. This preserves ESC verification
     of the rest of the method.
  2. **Extract the collection operation into a small private helper method** with its own spec
     (`requires`/`ensures`/`assignable`). Mark only the helper `skipesc`, keeping ESC active
     on the main method that calls it. This localizes the unverifiable code to the smallest
     possible surface.
  3. **Last resort: mark the entire method `skipesc`.** Always provide a full JML contract
     (`requires`, `ensures`, `assignable`) so callers can still reason about it modularly.

  See Section 16 (Verification Escalation) for the full decision process.

When hitting a limitation, work through the escalation ladder in Section 16 before resorting
to `skipesc`.

---

## 16. Verification Escalation: When ESC Fails

When OpenJML ESC cannot verify a method, work through these steps **in order**. Stop as soon
as verification succeeds. Each step trades some verification rigor for practicality; the goal
is to minimize how much you give up.

1. **Fix the spec or code.** Most ESC failures are real spec/implementation mismatches.
   Consult Section 11 (failure table) and Section 12 (common pitfalls) before assuming a tool
   limitation. Check for: overly strong postconditions, missing preconditions, incomplete
   `assignable` clauses, wrong loop invariant bounds, and visibility mismatches.

2. **Add intermediate `//@ assert` statements.** Break the proof into smaller steps. The
   prover may succeed on each sub-goal even when it fails on the whole method. Place asserts
   after key operations to confirm intermediate state.

3. **Simplify the code.** Refactor to avoid unsupported features (lambdas, streams, complex
   generics). Extract the problematic construct into a small helper method so verification
   difficulty is isolated.

4. **Use model or ghost fields.** Aid the proof with additional data and logic. The prover
   can often succeed when the problem is broken apart further. Additional data and logic
   provides necesary visibility into additional state or spec tracking.

5. **Use `//@ assume` for specific facts.** When the prover cannot derive a fact due to
   incomplete library specs or tool limitations, assert it with `assume`. Requirements:
   - Keep each `assume` as narrow and specific as possible.
   - Always add a comment explaining *why* the assume is needed and *what limitation* it
     works around.
   - Prefer `assume` over `skipesc` because it preserves ESC verification of the rest of the
     method -- only the assumed fact is taken on trust.

6. **Extract and isolate with `skipesc` on a helper.** When a single operation (e.g., a
   library call with incomplete specs) poisons the proof for the entire method, extract it
   into a small private helper method with its own JML contract. Mark only the helper
   `skipesc`. The calling method retains full ESC coverage and reasons about the helper
   through its contract.

7. **Use RAC instead of ESC.** For methods where ESC times out or hits fundamental tool
   limitations, runtime assertion checking (`--rac` / `--rac-run`) can still validate specs
   against concrete executions during testing.

8. **Apply `skipesc` to the method as a last resort.** When no other technique works, mark
   the method `skipesc`. **Requirements when using `skipesc`:**
   - The method **must** still have a full JML contract (`requires`, `ensures`, `assignable`)
     so callers can reason about it modularly.
   - Add a comment documenting what was tried and why `skipesc` is necessary.
   - Consider RAC coverage for the skipped method to retain some validation.

**Never start at step 8.** The steps above are ordered from most rigorous to least. Jumping
to `skipesc` early silently removes a method from formal verification and can mask real bugs.

---

## 17. Further Reference

- **OpenJML Tutorial:** https://www.openjml.org/tutorial/ -- Worked examples for all major
  features, from basic postconditions through ghost variables.
- **JML Reference Manual:** https://www.openjml.org/documentation/JML_Reference_Manual.pdf --
  Complete syntax and semantics for all JML constructs.
- **OpenJML User Guide:** https://www.openjml.org/documentation/OpenJMLUserGuide.pdf --
  Tool options, configuration, and integration with build systems.
- **OpenJML GitHub:** https://github.com/OpenJML/OpenJML -- Source, issues, and release builds.
