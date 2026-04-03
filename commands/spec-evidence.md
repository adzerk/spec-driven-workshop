---
description: Annotate OpenSpec Scenarios with supporting evidence, in the form of links to source code, tests, and examples
agent: build
subtask: true
---

Update the spec $1 such that each Scenario has a new subsection called Evidence (written as `##### Evidence`).  If a Scenario already has an Evidence subsection, update and refresh the information.
The Evidence section should contain a list of links to the code that implements that scenario and the tests that verify the scenario works as specified.
Group the tests by type ("Test", "Test (property)", etc.).  Multiple entries should be comma-separated.
Optionally, the Evidence section can contain an example of the code using a Markdown code block.  These code blocks can be treated as doctests and exercised by the `scripts/evidence.sh` shell script. See the script's `--help` for details on how to write "expectation comments".

Look in these locations first for code and tests before more broadly exploring the code base: $ARGUMENTS
Look at the recent git history to see what other specs, code, or tests should be analyzed and potentially updated.

If a Scenario is not implemented or doesn't have tests, it will not have "Implementation" or "Test" Evidence.

Here is an example Scenario with an Evidence block:

#### Scenario: Reject null initial state
- **WHEN** a caller creates a simulation with a null initial state
- **THEN** the library throws `NullPointerException`

##### Evidence
- Implementation: [Simulation.java:60 create(S initialState)](/src/main/java/com/kevel/des/Simulation.java#L60)
- Test: [SimulationTest.java:28 create_rejectsNullInitialState()](/src/test/java/com/kevel/des/SimulationTest.java#L28), [SimulationTest.java:44 create_validInitialState()](/src/test/java/com/kevel/des/SimulationTest.java#L44)
- Test (property): [SimulationPropertyTest.java:33 create_matchesMethodContract()](/src/test/java/com/kevel/des/SimulationPropertyTest.java#L33)
- Example:
```java
import com.kevel.des.Simulation;
var s = Simulation.create(null); //=> throws NullPointerException
```

Here is an example of a Scenario with an Evidence block, but with no tests for the specification:

#### Scenario: Initial simulation has a queue size of 0
- **WHEN** a caller creates a simulation with a valid initial state
- **THEN** the event queue is initialized empty and will have a size of 0

##### Evidence
- Implementation: [Simulation.java:60 create(S initialState)](/src/main/java/com/kevel/des/Simulation.java#L60)
- Example:
```java
import com.kevel.des.Simulation;
var s = Simulation.create(10); //=> type Simulation
s.queueSize(); //=> 0
```

