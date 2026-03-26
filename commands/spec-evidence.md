---
description: Annotate OpenSpec Scenarios with supporting evidence, in the form of links to source code, tests, and examples
agent: build
subtask: true
---

Update the spec $1 such that each Scenario has a new subsection called Evidence (written as `##### Evidence`).
The Evidence section should contain a list of links to the code that implements that scenario and the tests that verify the scenario works as specified.
Optionally, the Evidence section can contain an example of the code using a Markdown code block.

Look in these locations first for code and tests before more broadly exploring the code base: $ARGUMENTS

Here is an example Scenario with an Evidence block:

#### Scenario: Reject null initial state
- **WHEN** a caller creates a simulation with a null initial state
- **THEN** the library throws `NullPointerException`

##### Evidence
- Implementation: [Simulation.java:60 create(S initialState)](/src/main/java/com/kevel/des/Simulation.java#L60)
- Test: [SimulationTest.java:28 create_rejectsNullInitialState()](/src/test/java/com/kevel/des/SimulationTest.java#L28)
- Example:
```java
import com.kevel.des.Simulation;
var s = Simulation.create(null); //=> throws NullPointerException
```

