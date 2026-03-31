# Lightweight Formal Methods in Practice

This guide explains how lightweight formal methods fit into a spec-driven workflow. It complements [`docs/java_style.md`](java_style.md) and [`docs/state_machines.md`](state_machines.md) by focusing on workflow, artifacts, and evidence rather than code-level rules.

Lightweight formal methods are not about proving an entire system correct. They are about making the critical parts of a system explicit enough that they can be checked mechanically and evolved safely. Many costly failures begin in requirements, assumptions, and design, well before code is produced. So the goal is not universal proof -- The goal is to identify the critical properties, produce direct evidence that they hold, and keep that evidence alive as the system changes.

Recent industrial examples converge on the same pattern. Brooker emphasizes [invariant-first reasoning](https://brooker.co.za/blog/2023/07/28/ds-testing.html). AWS/S3 shows that a live product team can [use executable reference models](https://www.amazon.science/publications/using-lightweight-formal-methods-to-validate-a-key-value-storage-node-in-amazon-s3) and automated checks without aiming for full formal verification. Cedar shows [verification-guided development](https://www.amazon.science/publications/how-we-built-cedar-a-verification-guided-approach) around an executable model, proofs, differential random testing, and property-based testing. Datadog/`redis-rust` shows the [same idea in the age of agents](https://www.datadoghq.com/blog/ai/harness-first-agents/): generated code must answer to objective, mechanical pass/fail checks, and the verification harness matters as much as the implementation.

In a spec-driven and agent-assisted workflow, this matters even more. Code is cheaper to generate. Trust is not.

## Direct evidence is best

A system is dependable only when there is good reason to trust it. Processes, tools, and standards matter, but they are indirect. The stronger question is: what evidence do we have that this system preserves its critical properties in the situations that matter?

That evidence can take many forms:

- a clearly stated claim about a critical property;
- explicit assumptions about the environment, operators, and dependencies;
- a small executable model or other checkable specification;
- a proof, model-checking result, or static analysis result for a tractable core;
- a differential test showing that implementation and model agree;
- property-based tests over histories, not just isolated calls;
- a fault-injection seed or schedule that exercises a risky path;
- contracts, assertions, and review notes that explain why the argument is credible.

Treat this evidence as a work product. For a nontrivial change, the outcome should be a small dependability case: the claims, assumptions, models, checks, and results that justify trust in the change.

This is also why [a direct approach](https://cacm.acm.org/research/a-direct-path-to-dependable-software/) rewards innovation. The team is judged by the value/outcome delivered and the quality of its evidence, not by blind adherence to one prescribed technique.

## Start from critical properties and invariants

The first step is not choosing a tool. The first step is identifying what must be true.

Ask:

- What must always be true for the system to be correct?
- What must never happen?
- What states exist?
- What transitions are legal?
- What are the critical or defining properties of this entity?
- What [quality attributes](https://en.wikipedia.org/wiki/List_of_system_quality_attributes) are important and why? (limits, rates, security, reliability, etc.)
- What are the [interaction protocols](https://www.infoq.com/presentations/history-protocols-distributed-systems/) between these entities?
- What failures are acceptable, and which are intolerable?
- What assumptions are we making about users, infrastructure, time, randomness, and the environment?
- Which properties matter end to end, in the world, not only inside the code?

Critical properties are usually a better focus than feature lists. Features change. Properties such as "the billed amount matches the authorized amount," "a committed event is not lost," or "the delivered result respects the stated policy" retain their meaning as the system evolves.

Invariants should be first-class -- both informally and formally.

## Express properties at the right level

When the system interacts with the real world, express critical properties in terms that stay close to the real outcome that matters, not only the internal signals closest to the code.

That matters because:

- software often sits inside a larger system of people, devices, and services;
- assumptions hide in the gap between internal signals and real-world effects;
- a property that is easy to state in software terms may be too weak to justify trust.

This is why system boundaries and assumptions matter. If correctness depends on an operator behaving a certain way, or on middleware preserving an ordering guarantee, that belongs in the argument too.

## Build a small executable model

Once the critical core is identified, build a small model of it. The model should simplify nonessential details so that the important behavior is obvious and checkable.

A good reference model usually:

- replaces infrastructure with interfaces and simple data structures;
- uses pure or near-pure functions;
- makes state explicit;
- is single-threaded and value-oriented;
- focuses on semantics, not performance;
- is small enough to read in one sitting;
- is easier to trust than the production implementation.

The model is not the product. It enables us to see how a design withstands reality. It is the oracle that makes later checking possible.

Depending on the problem, the model might be:

- a small executable program in the host language (Java, Rust, etc.);
- a model in a verification-oriented language ([Alloy](https://practicalalloy.github.io/index.html), [TLA+](https://learntla.com/), Lean);
- a state table or algebraic model for a narrow kernel;
- a simplified protocol model for concurrency, recovery, or permissions.

The notation matters less than the fact that the behavior is explicit and can be checked.  You can use the agent to automate building models.

## Shape the implementation for reasoning

Lightweight formal methods work best when the production system is built in a form that humans and tools can reason about.

Prefer:

- explicit state machines;
- deterministic cores;
- clear ownership and ordering rules;
- side effects and nondeterminism pushed to the edges (I/O, time, dynamic/conditional/flag handling behavior, etc.);
- small interfaces and localized invariants;
- decoupled components whose behavior can be understood in isolation.

Decoupling and simplicity are not aesthetic preferences. They lower the cost of assurance by localizing critical properties. If a property is spread across the whole codebase, the whole codebase becomes critical. If it is localized to one component or one interaction boundary, verification and review can focus there.

This is why there is emphasis on deterministic systems, state machines, explicit error handling, and strong contracts in [`docs/java_style.md`](java_style.md) and [`docs/state_machines.md`](state_machines.md). Those guides explain how to write the code. This guide explains why that shape matters and how to drive it from the spec.

## Connect the model to the implementation

A model is only useful if it remains connected to the real system. In practice, the most powerful bridge is differential testing: run the same operations, histories, or scenarios against both the model and the implementation and compare the results.

This is often the highest-leverage step in the whole stack because it turns the model into a living oracle for the implementation.

For agent-assisted development, this is especially important:

- the agent can generate plausible code quickly;
- the model provides a simpler statement of intended behavior;
- differential tests let the harness, not the agent, decide whether the implementation matches the spec.

The harness is the trust boundary.

## Use a verification pyramid

No single technique is enough. Testing alone is not enough for high assurance. Proofs alone are not enough either because they depend on scope, assumptions, and the sufficiency of the stated property. The practical answer is a layered stack of evidence.

A typical verification pyramid looks like this:

1. explicit claims, assumptions, bounds, and invariants;
2. a small executable model or other checkable spec;
3. proofs or model checking for the tractable core;
4. differential testing between model and implementation;
5. property-based tests over histories, sequences, and edge cases;
6. concurrency testing, deterministic simulation, or fault injection where relevant;
7. regression tests for every discovered counterexample;
8. static analysis, contracts, and compile-time checks;
9. CI automation so the evidence is re-checked on every change.

Use the lightest mechanism that can falsify the current hypothesis, then layer stronger mechanisms where the risk justifies them.

For a Java-based project, that usually means JUnit for examples and regressions, jqwik for generated histories, Fray for concurrency schedules, and OpenJML for contracts and extended static checking, as described in [`docs/java_style.md`](java_style.md).

## Testing remains essential, but it is not enough

Testing is crucial. It catches regressions, supports iteration, and gives fast feedback. But testing alone rarely gives enough confidence for the most critical properties at a reasonable cost.  Tests cover individual cases, [proofs represent "for all cases"](https://cacm.acm.org/blogcacm/a-fundamental-duality-of-software-engineering/).

As assurance demands rise:

- the state space grows too quickly;
- coverage becomes a weak proxy for correctness;
- real failures hide in combinations of state, timing, and assumptions;
- passing tests say little about nearby but untested cases.

This is why lightweight formal methods do not replace testing. They make testing more valuable by giving it sharper targets:

- invariants to check after every step;
- reference models to compare against;
- histories to generate;
- faults and schedules/interleavings to inject;
- contracts to monitor at important boundaries.

A single well-chosen invariant can stand in for an infinite family of test cases.

## Assumptions, scope, and tool credibility matter

More mechanization generally means more credible evidence, but the tool result is never the whole story.

Always ask:

- What exact property was checked?
- Is that property sufficient for the end-to-end claim we care about?
- What assumptions were made about arithmetic, memory, middleware, time, scheduling, operators, or the environment?
- Are those assumptions explicit, justified, and reviewable?
- If the tool is noisy, are real defects getting buried in false alarms?
- If the tool is silent, are we sure the property is meaningful?

Even strong proofs can miss problems if the wrong property was formalized or if a hidden assumption turns out to be false. Lightweight formal methods work best when evidence is both mechanically checked and critically reviewed.

## Guidance for engineers

For engineers, the practical workflow is:

1. Identify the critical core; Be risk-driven here.
2. Draw a picture; Write down the requirements, quality attributes, critical properties, assumptions, and invariants.
3. Build a small model or executable spec for the behavior/core system.
4. Design the production system so those properties are local, explicit, and checkable.
5. Implement the deterministic core and push nondeterminism to the edges.
6. Compare model and implementation with differential tests.
7. Add property-based tests, fault injection, and schedule/interleaving exploration where bugs are likely to hide.
8. Turn every counterexample into a permanent regression test.
9. Keep the entire evidence stack in CI.
10. Update the spec and the evidence as the system evolves.

The point is not to maximize formalism. The point is to maximize justified confidence per unit of effort.

## Guidance for coding agents

Agents should be used to amplify this workflow, not bypass it.

Use agents to:

- restate requirements as critical properties and invariants;
- surface ambiguities and missing assumptions;
- generate small reference models or skeleton specs;
- draft contracts, assertions, specs, and test oracles;
- generate differential, property-based, and concurrency tests;
- explain failing seeds, shrunk counterexamples, or proof obligations;
- summarize evidence gaps after each implementation pass.

Do not ask agents to only "implement the feature." Ask them to:

- identify invariants;
- document preconditions and postconditions;
- keep the core deterministic;
- isolate I/O and other nondeterminism;
- produce the harness that will judge the implementation.

And do not let an agent self-certify. The code it wrote must answer to an independent check: a model, a verifier, a test harness, a static analyzer, and/or a review from a fresh context.
See also: [When AI Writes the World's Software, Who Verifies It?](https://leodemoura.github.io/blog/2026-2-28-when-ai-writes-the-worlds-software-who-verifies-it/)

## Lightweight formal methods in the DESIRED workflow

The spec-driven workflow already provides the right structure for lightweight formal methods. DESIRED makes the evidence-building process explicit.

### Draw / Invariants

Start with the system boundary, the major components, the critical properties, the important quality attributes, the legal transitions, the bounds, and the dangerous failure modes. This is where the initial dependability claims are born.

### Explore

Probe the design with agents, quick models (in code or with Alloy), REPL experiments, and thought experiments. Try vague prompts first, then tighten them by adding more requirements and invariants. Use this phase to discover missing invariants, hidden assumptions, and alternative formulations of the core rules.
During this phase, use the agent to produce 10s/100s of prototypes to see the ideas and implications come to life -- iterate with the Plan agent to generate a "plan.md" and build it.

### Spec

Turn the refined understanding (eg: your best "plan.md" file) into small, focused spec artifacts -- the agent can automate all of this with `/opsx-propose`. The spec should say what matters, what must be preserved, what assumptions hold, and how success will be judged. The OpenSpec artifacts are a natural place to carry the dependability case forward: the proposal captures why, the design captures structure and reasoning, the spec records critical properties and assumptions, and the tasks drive construction and evidence-producing work.
The spec.md artifact can be further enhanced by embedding an Alloy model of the system (automated with `/spec-model`).  Have the agents review the spec artifacts for completeness, correctness, and consistency.

### Implement

Have the agent implement against the spec using `/opsx-apply`, not against an underspecified feature request. Require documented preconditions, postconditions, and invariants. Keep the core deterministic. Push time, I/O, randomness, and scheduling to the edges. Generate the unit tests, property-tests, and contracts alongside the code. Code must always be accompanied with its evidence.
Have the agent tag tests with the `Scenario` or invariant being exercised.

### Review with agents

Use fresh-context review to attack the plan, the spec, the code, and the evidence. Ask what assumptions remain unjustified, which invariants are still only implicit, and where the implementation may be reinforcing itself rather than being independently checked.
Ensure the code quality is high, the solution quality is high, and everything is correct.

### Examine

Read the code, contracts, tests, model, and counterexamples together. Run the full check pipeline. Confirm that the evidence actually supports the claims you care about. If the system taught you something new, feed it back into the spec.
The `/spec-evidence` command and `scripts/evidence.sh` script can help enhance the spec to make this process easier/automatic.

### Done

Archive the change with its spec artifacts, model, contracts, tests, and review record. The result is not just code that works today, but living documentation of the system and its critical properties.  In combination, these explain why everything should keep working as the system evolves.

## How this guide complements the other docs/guides

This guide explains the workflow and the evidence model.

- [`docs/java_style.md`](java_style.md) explains how these ideas should show up in code: deterministic systems, explicit types, assertions, contracts, simulation, differential testing, concurrency discipline, and strong tooling.
- [`docs/state_machines.md`](state_machines.md) explains one of the most useful implementation styles for making states, transitions, and invariants explicit.

Use this guide to decide what artifacts to create, what claims to make, and what evidence to demand. Use those guides to shape the implementation that follows.

## What lightweight formal methods are not

They are not:

- a demand to prove the whole system;
- a replacement for testing;
- a license to trust a tool result without examining assumptions;
- a separate academic artifact divorced from the code;
- a one-time verification pass at the end of development.

They are a way of working: risk-driven, invariant-first, model-backed, evidence-seeking, and friendly to iteration.

## The practical takeaway

If code generation is getting cheaper, the bottleneck shifts from producing code to producing trustworthy evidence.

Lightweight formal methods are how we respond. We make the important properties explicit. We build small models of the critical core. We shape systems so they can be reasoned about. We connect model and implementation with differential testing. We layer tests, analysis, contracts, simulation, and proofs where they buy confidence. And we treat the resulting evidence as part of the product.

That is the direct path to more dependable software, and it fits naturally inside spec-driven development.
