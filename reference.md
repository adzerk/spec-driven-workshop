## The main points

* LLMs are not intelligent  
  * They are good at reading and writing.  They are Language models, not auto-computers.  You supply the intelligence.

* Invariants-first makes the most difference

* Iterate with the Plan agent, then Build – this is the foundation

* You can make 10s/100s of prototypes  
* Or any custom tool for the job. Or any additional verification. This is a superpower.

## The DESIRED Outcome

1. Draw / invariants first  
2. Explore and probe with the agent  
3. Specify using OpenSpec  
4. Implement from spec  
5. Review (agent-driven passes)  
6. Examine (human two-pane review; Augment with \`evidence\`)  
7. Done — complete and archive

## The necessary tooling support for success

From the guide:  
We need to have high confidence and assurance in the artifacts produced by our coding agents and also support the agents with as much automated, directed feedback as possible.  We’re going to achieve that through a number of tools and techniques:

* Strong compilers with compile-time warnings and messages to guide implementation  
* Test suites that cover basic invariant checks with unit tests, property-based testing of deeper behaviors and state machines, and concurrency testing  
* Static analyzers to catch defects, problematic coding patterns, security issues, etc.  
* Code formatters to keep syntax uniform across the code base  
* Extended checking with code verifiers and similar tools  
* Subagents for code-review, testing, and code optimization passes (we’ll cover these in the workshop)

Regardless of the language and runtime of your project, you should have support for each of these critical areas.  You also need a [strong sandbox](GettingStartedWithSpec-DrivenDev.md#safety-first-sandboxing).