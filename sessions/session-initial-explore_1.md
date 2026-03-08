# Single-threaded discrete event simulation library design in Java 21

**Session ID:** ses_331f4d70bffen1HZ4KIki1udpH
**Created:** 3/8/2026, 11:22:43 AM
**Updated:** 3/8/2026, 11:34:40 AM

---

## User

Design and plan a simple discrete event simulation library in Java 21.  The core implementation should be functional. The implementation should be single-threaded.  Explicitly document pre-conditions, post-conditions, and invariants. 

---

## Assistant (Plan · gpt-5.2 · 42.5s)

_Thinking:_

**Planning simulation design**

I need to focus on creating a plan without making edits. I can use tools to read and inspect, but that seems unnecessary since I can work from my knowledge. I'm aiming for a simple DES library in Java 21 with careful documentation on invariants. The design should be single-threaded and use a priority queue for events. I want to maintain a functional style, utilizing immutable event objects and pure functions for event handling while also considering precision in time representation.

_Thinking:_

**Designing simulation architecture**

I’m thinking about creating a `Simulation<S>` class that tracks current time, state, and an event queue. I want a method like `scheduleAt(long, UnaryOperator<S>)` that returns a new, persistent simulation, but I worry about the immutability of the priority queue. Perhaps I can also have a `SimEngine<S>` with mutable internals, exposing functional scheduling. I need to document invariants—like time being monotonic and ensuring FIFO for events scheduled at the same time—and clearly outline preconditions and postconditions. I'll include functionality for running until a certain time or predicate is met.

_Thinking:_

**Crafting a simple plan**

I’m aiming to keep things straightforward, without any cancellation functionality. It’s crucial to explicitly document preconditions, postconditions, and invariants in Javadoc, and possibly use assertions in the code. I want to mention using Java 21 records and sealed interfaces as well. I might consider adding a deterministic random generator if needed, but it's not a must-have. For time representation, I'll propose using long ticks with an arbitrary default. My plan will consist of five steps: API design, core algorithm, documentation and validation, examples, and tests, including sketches of class signatures.

**Goal**
A tiny, single-threaded discrete-event simulation (DES) library for Java 21 with a “functional core”: model updates are pure functions `S -> S`, and the engine is a deterministic interpreter that applies them in timestamp order.

---

## Design Principles

- Single-threaded, deterministic execution.
- Functional core: every event carries a pure state transition (`UnaryOperator<S>`). The only “mutation” is the engine loop applying transitions.
- Explicit time ordering: `(time, sequence)` total order; stable FIFO for equal times.
- Minimal surface area: scheduling + stepping + running; avoid cancellation and concurrency in v1.

---

## Public API (Proposed)

### Core Types

```java
package des;

import java.util.function.UnaryOperator;

public record EventId(long value) {}

public record Event<S>(
    long time,               // simulation time in ticks
    long sequence,           // tie-breaker, strictly increasing
    EventId id,
    UnaryOperator<S> action  // pure state transition
) {}
```

```java
package des;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class Simulation<S> {
  public static <S> Simulation<S> create(S initialState);

  public long now();         // current simulation time (ticks)
  public S state();          // current model state (immutable-by-convention)
  public boolean isEmpty();  // no pending events

  public EventId scheduleAt(long time, UnaryOperator<S> action);
  public EventId scheduleIn(long delay, UnaryOperator<S> action);

  public Optional<Event<S>> peekNext();
  public StepResult<S> step();                 // executes exactly one event
  public RunResult<S> run();                   // until queue empty
  public RunResult<S> runUntilTime(long tEnd); // executes events with time <= tEnd
  public RunResult<S> runUntil(Predicate<Simulation<S>> stopCondition);
}
```

```java
package des;

public record StepResult<S>(
    long previousTime,
    long newTime,
    EventId executedEventId,
    S previousState,
    S newState
) {}

public record RunResult<S>(
    long stepsExecuted,
    long startTime,
    long endTime,
    S finalState,
    boolean stoppedByCondition
) {}
```

Notes:
- `Simulation<S>` can be mutable internally but should *behave* like a deterministic interpreter of pure actions.
- `S` is “immutable-by-convention”; library won’t enforce deep immutability, but docs will require it for functional semantics.

---

## Core Algorithm

### Data Structure
- One `PriorityQueue<Event<S>>` ordered by:
  1) `time` ascending
  2) `sequence` ascending (assigned at scheduling time; strictly increasing)

### Step Semantics
- Pop next event `e`.
- Set `now = e.time`.
- Compute `newState = e.action.apply(oldState)`.
- Replace `state = newState`.
- Return `StepResult`.

### Run Semantics
- Repeatedly `step()` while:
  - queue non-empty, and
  - `nextEvent.time <= tEnd` (for time-bounded runs), and
  - `!stopCondition.test(this)` (for predicate runs)

Determinism guarantee: with the same initial state and same schedule calls in the same order, results are identical.

---

## Contracts: Preconditions, Postconditions, Invariants

### Simulation Invariants (always true after construction)
- `now` is monotonically non-decreasing across `step()` calls.
- Every enqueued event `e` satisfies `e.time >= now` (no scheduling in the past).
- `sequence` is strictly increasing for every scheduled event (global within a `Simulation` instance).
- Queue ordering is a strict total order by `(time, sequence)`; no ambiguity for same-time execution order.
- Single-threaded usage only (no concurrent calls).

### `create(initialState)`
- Preconditions:
  - `initialState != null`
- Postconditions:
  - `now == 0`
  - `state == initialState`
  - queue empty
  - next `sequence` starts at 0 or 1 (implementation-defined, documented)

### `scheduleAt(time, action)`
- Preconditions:
  - `action != null`
  - `time >= now`
- Postconditions:
  - returns non-null `EventId`
  - exactly one event is added with `event.time == time`
  - event’s `(time, sequence)` is >= any previously scheduled event’s `(time, sequence)` in lexicographic order of `sequence` (sequence monotone)
  - queue size increases by 1

### `scheduleIn(delay, action)`
- Preconditions:
  - `action != null`
  - `delay >= 0`
  - `now + delay` does not overflow `long` (either: precondition, or library throws)
- Postconditions:
  - equivalent to `scheduleAt(now + delay, action)`

### `peekNext()`
- Preconditions: none
- Postconditions:
  - returns empty iff queue empty
  - does not modify simulation

### `step()`
- Preconditions:
  - queue not empty
- Postconditions:
  - exactly one event is removed (the minimum by `(time, sequence)`)
  - `now` becomes that event’s `time`
  - `state` becomes `event.action.apply(previousState)`
  - invariants preserved

### `run*()`
- Preconditions: depend on variant (`tEnd >= now` for `runUntilTime`)
- Postconditions:
  - executes zero or more steps
  - stops with queue empty, time bound reached, or predicate true (variant-specific)
  - invariants preserved

### Error Handling (recommended)
- Throw `NullPointerException` for null `action`/`initialState` (idiomatic with `Objects.requireNonNull`).
- Throw `IllegalArgumentException` for “schedule in past”, negative delay, or `tEnd < now`.
- Throw `ArithmeticException` on `now + delay` overflow (use `Math.addExact`).

---

## “Functional Core” Guidance (documented as requirements on users)
To keep the simulation “functional” in practice:
- Prefer immutable `S` (records, persistent structures) or treat it as immutable-by-convention.
- Event actions should be pure: no I/O, no reading wall-clock time, no mutation of captured external state.
- If side effects are needed, route them through an explicit “effect log” in state (e.g., `S` contains `List<Effect>`), then interpret effects outside the engine.

(You can still allow side effects, but you’ll document that doing so breaks referential transparency, not correctness.)

---

## Package/Layout Plan

- `des/EventId.java` (record)
- `des/Event.java` (record)
- `des/Simulation.java` (engine + queue)
- `des/StepResult.java` (record)
- `des/RunResult.java` (record)
- (Optional) `des/Ordering.java` (private comparator helper)
- (Optional) `des/examples/*` or tests demonstrating usage

---

## Minimal Example (Target Usage)

```java
record Model(long customersServed) {}

Simulation<Model> sim = Simulation.create(new Model(0));

sim.scheduleAt(10, m -> new Model(m.customersServed() + 1));
sim.scheduleIn(5,  m -> new Model(m.customersServed() + 1));

RunResult<Model> r = sim.run();

System.out.println(r.finalState().customersServed()); // deterministic
```

---

## Validation Plan (tests)
- Ordering: equal-time events execute in schedule order (sequence).
- Time monotonicity after multiple steps.
- Reject scheduling in the past.
- `runUntilTime(tEnd)` executes only events with `time <= tEnd`.
- Overflow behavior for `scheduleIn`.

---

## One Decision Needed (small but important)
What should “time” be in v1?

1) `long` ticks (Recommended): simplest, deterministic, no floating-point edge cases.
2) `double` time: more convenient for continuous-time modeling, but ordering/tie issues.
3) `Duration`: expressive but heavier; still needs conversion/ordering rules.

If you don’t specify, I’ll proceed with option 1 (`long` ticks) and document “unit is user-defined”.

---

## User

Proceed with Option 1 (long ticks).  Please extend the validation plan to include unit tests for basic invariants and constraints using JUnit 5, and validate the full state machine of the DES with property-based testing using jqwik.

---

## Assistant (Plan · gpt-5.2 · 5.2s)

_Thinking:_



**Tool: glob**

**Input:**
```json
{
  "pattern": "pom.xml"
}
```

**Output:**
```
/home/paul/scratch/spec-driven-workshop/pom.xml
```

**Tool: glob**

**Input:**
```json
{
  "pattern": "build.gradle*"
}
```

**Output:**
```
No files found
```

**Tool: glob**

**Input:**
```json
{
  "pattern": "settings.gradle*"
}
```

**Output:**
```
No files found
```

**Tool: glob**

**Input:**
```json
{
  "pattern": "gradlew*"
}
```

**Output:**
```
No files found
```

**Tool: glob**

**Input:**
```json
{
  "pattern": "**/src/test/java/**"
}
```

**Output:**
```
/home/paul/scratch/spec-driven-workshop/src/test/java/com/kevel/AppTest.java
```

**Tool: glob**

**Input:**
```json
{
  "pattern": "**/src/main/java/**"
}
```

**Output:**
```
/home/paul/scratch/spec-driven-workshop/src/main/java/com/kevel/App.java
```

---

## Assistant (Plan · gpt-5.2 · 1.0s)

**Tool: read**

**Input:**
```json
{
  "filePath": "/home/paul/scratch/spec-driven-workshop/pom.xml"
}
```

**Output:**
```
<path>/home/paul/scratch/spec-driven-workshop/pom.xml</path>
<type>file</type>
<content>1: <project xmlns="http://maven.apache.org/POM/4.0.0"
2:          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
3:          xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
4:   <modelVersion>4.0.0</modelVersion>
5: 
6:   <groupId>com.kevel</groupId>
7:   <artifactId>spec-driven-demo</artifactId>
8:   <version>1.0.0-SNAPSHOT</version>
9:   <packaging>jar</packaging>
10: 
11:   <properties>
12:     <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
13: 
14:     <maven.compiler.plugin.version>3.14.0</maven.compiler.plugin.version>
15:     <maven.compiler.release>21</maven.compiler.release>
16:     <maven.compiler.parameters>true</maven.compiler.parameters>
17: 
18:     <maven.surefire.plugin.version>3.5.5</maven.surefire.plugin.version>
19:     <junit.jupiter.version>5.14.3</junit.jupiter.version>
20:     <jqwik.version>1.9.3</jqwik.version>
21:     <fray.version>0.7.3</fray.version>
22: 
23:     <spotbugs.maven.plugin.version>4.9.8.2</spotbugs.maven.plugin.version>
24: 
25:     <errorprone.version>2.48.0</errorprone.version>
26: 
27:     <spotless.maven.plugin.version>3.3.0</spotless.maven.plugin.version>
28:     <google.java.format.version>1.35.0</google.java.format.version>
29:     <palantir.java.format.version>2.39.0</palantir.java.format.version>
30: 
31:     <maven.shade.version>3.6.2</maven.shade.version>
32:   </properties>
33: 
34:   <dependencies>
35:     <dependency>
36:       <groupId>org.junit.jupiter</groupId>
37:       <artifactId>junit-jupiter</artifactId>
38:       <version>${junit.jupiter.version}</version>
39:       <scope>test</scope>
40:     </dependency>
41:     <dependency>
42:       <groupId>net.jqwik</groupId>
43:       <artifactId>jqwik</artifactId>
44:       <version>${jqwik.version}</version>
45:       <scope>test</scope>
46:     </dependency>
47:     <dependency>
48:       <groupId>org.pastalab.fray</groupId>
49:       <artifactId>fray-junit</artifactId>
50:       <version>${fray.version}</version>
51:       <scope>test</scope>
52:     </dependency>
53:   </dependencies>
54: 
55:   <profiles>
56: 
57:     <!-- TODO: This profile is to scope OpenJML in isolation, but it's not actively used -->
58:     <profile>
59:       <id>openjml</id>
60:       <build>
61:         <plugins>
62:           <plugin>
63:             <groupId>org.apache.maven.plugins</groupId>
64:             <artifactId>maven-compiler-plugin</artifactId>
65:             <version>${maven.compiler.plugin.version}</version>
66:             <configuration>
67:               <release>${maven.compiler.release}</release>
68: 
69:               <fork>true</fork>
70:               <executable>${project.basedir}/tooling/openjml/openjml</executable>
71:               <compilerArgs>
72:                 <arg>--esc</arg>
73:                 <arg>--progress</arg>
74:               </compilerArgs>
75:             </configuration>
76:           </plugin>
77:         </plugins>
78:       </build>
79:     </profile>
80: 
81:     <!-- This profile configures Google Errorprone as part of the compilation phase.
82:          Errorprone is a static analyzer that extends Java's type checking analysis. -->
83:     <profile>
84:       <id>errorprone</id>
85:       <build>
86:         <plugins>
87:           <plugin>
88:             <groupId>org.apache.maven.plugins</groupId>
89:             <artifactId>maven-compiler-plugin</artifactId>
90:             <version>${maven.compiler.plugin.version}</version>
91:             <configuration>
92:               <release>${maven.compiler.release}</release>
93: 
94:               <fork>true</fork>
95:               <executable>${project.basedir}/tooling/jdk-21.0.7+6/bin/javac</executable>
96:               <compilerArgs>
97:                 <arg>-XDcompilePolicy=simple</arg>
98:                 <arg>--should-stop=ifError=FLOW</arg>
99:                 <arg>-Xplugin:ErrorProne</arg>
100:                 <arg>-XDaddTypeAnnotationsToSymbol=true</arg>
101: 
102:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED</arg>
103:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED</arg>
104:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED</arg>
105:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED</arg>
106:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED</arg>
107:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED</arg>
108:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED</arg>
109:                 <arg>-J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED</arg>
110:                 <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED</arg>
111:                 <arg>-J--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED</arg>
112:               </compilerArgs>
113: 
114:               <annotationProcessorPaths>
115:                 <path>
116:                   <groupId>com.google.errorprone</groupId>
117:                   <artifactId>error_prone_core</artifactId>
118:                   <version>${errorprone.version}</version>
119:                 </path>
120:               </annotationProcessorPaths>
121: 	        </configuration>
122: 
123:             <dependencies>
124:               <!--
125:               <dependency>
126:                 <groupId>org.codehaus.plexus</groupId>
127:                 <artifactId>plexus-compiler-javac-errorprone</artifactId>
128:                 <version>${plexus.errorprone.compiler.version}</version>
129:                   </dependency>
130:               -->
131:               <dependency>
132:                 <groupId>com.google.errorprone</groupId>
133:                 <artifactId>error_prone_core</artifactId>
134:                 <version>${errorprone.version}</version>
135:               </dependency>
136:             </dependencies>
137:           </plugin>
138:         </plugins>
139:       </build>
140:     </profile>
141: 
142:     <!-- Dev-time profile for interactive use / Clojure REPL access -->
143:     <profile>
144:       <id>dev</id>
145:       <build>
146:         <plugins>
147:           <plugin>
148:             <groupId>com.github.johnpoth</groupId>
149:             <artifactId>jshell-maven-plugin</artifactId>
150:             <version>1.4</version>
151:           </plugin>
152:           <plugin>
153:             <groupId>com.theoryinpractise</groupId>
154:             <artifactId>clojure-maven-plugin</artifactId>
155:             <version>1.9.3</version>
156:             <extensions>true</extensions>
157:           </plugin>
158:         </plugins>
159:       </build>
160:       <dependencies>
161:         <dependency>
162:           <groupId>org.clojure</groupId>
163:           <artifactId>clojure</artifactId>
164:           <version>1.12.4</version>
165:         </dependency>
166:       </dependencies>
167:     </profile>
168:   </profiles>
169: 
170:   <build>
171:     <plugins>
172:       <plugin>
173:         <groupId>org.apache.maven.plugins</groupId>
174:         <artifactId>maven-compiler-plugin</artifactId>
175:         <version>${maven.compiler.plugin.version}</version>
176:         <configuration>
177:           <release>${maven.compiler.release}</release>
178: 
179:           <fork>true</fork>
180:           <executable>${project.basedir}/tooling/jdk-21.0.7+6/bin/javac</executable>
181:         </configuration>
182:       </plugin>
183: 
184:       <!-- Surefire is the plugin for running JUnit 5 test suites and other tools that integrate into JUnit5 -->
185:       <plugin>
186:         <groupId>org.apache.maven.plugins</groupId>
187:         <artifactId>maven-surefire-plugin</artifactId>
188:         <version>${maven.surefire.plugin.version}</version>
189:         <configuration>
190:           <useModulePath>false</useModulePath>
191:         </configuration>
192:       </plugin>
193:       <!-- Fray is a concurrency testing tool -->
194:       <plugin>
195:         <groupId>org.pastalab.fray.maven</groupId>
196:         <artifactId>fray-plugins-maven</artifactId>
197:         <version>${fray.version}</version>
198:         <executions>
199:           <execution>
200:             <id>prepare-fray</id>
201:             <goals>
202:               <goal>prepare-fray</goal>
203:             </goals>
204:           </execution>
205:         </executions>
206:       </plugin>
207: 
208:       <plugin>
209:         <groupId>org.apache.maven.plugins</groupId>
210:         <artifactId>maven-javadoc-plugin</artifactId>
211:         <version>3.12.0</version>
212:         <configuration>
213:             <!-- Custom configurations can go here -->
214:         </configuration>
215:       </plugin>
216: 
217:       <!-- Static Analyzers: SpotBugs + FindSecurityBugs -->
218:       <plugin>
219:         <groupId>com.github.spotbugs</groupId>
220:         <artifactId>spotbugs-maven-plugin</artifactId>
221:         <version>${spotbugs.maven.plugin.version}</version>
222:         <configuration>
223:           <effort>Max</effort>
224:           <threshold>Medium</threshold>
225:           <failOnError>true</failOnError>
226:           <!--
227:           <includeFilterFile>${session.executionRootDirectory}/spotbugs-include.xml</includeFilterFile>
228:           <excludeFilterFile>${session.executionRootDirectory}/spotbugs-exclude.xml</excludeFilterFile>
229:           -->
230:           <plugins>
231:             <plugin>
232:               <groupId>com.h3xstream.findsecbugs</groupId>
233:               <artifactId>findsecbugs-plugin</artifactId>
234:               <version>1.14.0</version>
235:             </plugin>
236:           </plugins>
237:         </configuration>
238:         <executions>
239:           <execution>
240:             <id>spotbugs-check</id>
241:             <phase>verify</phase>
242:             <goals>
243:               <goal>check</goal>
244:             </goals>
245:           </execution>
246:         </executions>
247:       </plugin>
248: 
249:       <!-- Spotless is a code formatting tool used to keep all coding styles uniform -->
250:       <plugin>
251:         <groupId>com.diffplug.spotless</groupId>
252:         <artifactId>spotless-maven-plugin</artifactId>
253:         <version>${spotless.maven.plugin.version}</version>
254:         <configuration>
255:           <java>
256:             <!--
257:             <googleJavaFormat>
258:               <version>${google.java.format.version}</version>
259:             </googleJavaFormat>
260:             -->
261:             <palantirJavaFormat>
262:               <version>${palantir.java.format.version}</version>
263:               <style>PALANTIR</style>
264:               <formatJavadoc>false</formatJavadoc>
265:             </palantirJavaFormat>
266:           </java>
267:         </configuration>
268:         <executions>
269:           <execution>
270:             <id>spotless-check</id>
271:             <phase>verify</phase>
272:             <goals>
273:               <goal>check</goal>
274:             </goals>
275:           </execution>
276:         </executions>
277:       </plugin>
278: 
279:       <!-- Shade is used to produce an Uberjar / Fat Jar during packaging -->
280:       <plugin>
281:         <groupId>org.apache.maven.plugins</groupId>
282:         <artifactId>maven-shade-plugin</artifactId>
283:         <version>${maven.shade.version}</version>
284:         <executions>
285:           <execution>
286:             <phase>package</phase>
287:             <goals>
288:               <goal>shade</goal>
289:             </goals>
290:             <configuration>
291:               <transformers>
292:                 <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
293:                   <mainClass>com.kevel.App</mainClass>
294:                 </transformer>
295:               </transformers>
296:             </configuration>
297:           </execution>
298:         </executions>
299:       </plugin>
300:     </plugins>
301:   </build>
302: 
303: </project>

(End of file - total 303 lines)
</content>
```

---

## Assistant (Plan · gpt-5.2 · 1.1s)

**Tool: read**

**Input:**
```json
{
  "filePath": "/home/paul/scratch/spec-driven-workshop/src/test/java/com/kevel/AppTest.java"
}
```

**Output:**
```
<path>/home/paul/scratch/spec-driven-workshop/src/test/java/com/kevel/AppTest.java</path>
<type>file</type>
<content>1: package com.kevel;
2: 
3: import static org.junit.jupiter.api.Assertions.assertEquals;
4: import static org.junit.jupiter.api.Assertions.assertTrue;
5: 
6: import net.jqwik.api.Arbitraries;
7: import net.jqwik.api.Arbitrary;
8: import net.jqwik.api.ForAll;
9: import net.jqwik.api.Property;
10: import net.jqwik.api.Provide;
11: import org.junit.jupiter.api.Test;
12: 
13: class AppTest {
14: 
15:     @Test
16:     void increasingSum_expectedUsage() {
17:         assertEquals(30, App.increasingSum(10, 20));
18:         assertEquals(40, App.increasingSum(20, 20));
19:         assertEquals(21, App.increasingSum(3, 20));
20:     }
21: 
22:     @Property
23:     void increasingSum_matchesMethodContract(@ForAll("validIncreasingInputs") IntPair input) {
24:         int result = App.increasingSum(input.x(), input.y());
25: 
26:         if (input.y() % input.x() == 0) {
27:             assertEquals(input.x() + input.y(), result);
28:         } else {
29:             assertEquals(input.y() + 1, result);
30:         }
31:     }
32: 
33:     @Property
34:     void increasingSum_isAlwaysGreaterThanY(@ForAll("validIncreasingInputs") IntPair input) {
35:         int result = App.increasingSum(input.x(), input.y());
36:         assertTrue(result > input.y());
37:     }
38: 
39:     @Provide
40:     Arbitrary<IntPair> validIncreasingInputs() {
41:         Arbitrary<Integer> xValues = Arbitraries.integers().between(1, 100_000);
42: 
43:         return xValues.flatMap(x -> {
44:             int maxY = Integer.MAX_VALUE - x;
45:             if (maxY < x) {
46:                 return Arbitraries.of();
47:             }
48: 
49:             return Arbitraries.integers().between(x, maxY).map(y -> new IntPair(x, y));
50:         });
51:     }
52: 
53:     private record IntPair(int x, int y) {}
54: }

(End of file - total 54 lines)
</content>
```

---

## Assistant (Plan · gpt-5.2 · 1.1s)

**Tool: read**

**Input:**
```json
{
  "filePath": "/home/paul/scratch/spec-driven-workshop/src/main/java/com/kevel/App.java"
}
```

**Output:**
```
<path>/home/paul/scratch/spec-driven-workshop/src/main/java/com/kevel/App.java</path>
<type>file</type>
<content>1: package com.kevel;
2: 
3: public final class App {
4:     private App() {}
5: 
6:     // Even code without JML specifications are analyzed and checked by OpenJML.
7:     // We can explicitly disable "Extended Static Checking" (esc) and
8:     // Runtime Assertion Checking (rac) for methods we want OpenJML to skip.
9: 
10:     // @ skipesc skiprac
11:     public static void main(String[] args) {
12:         System.out.println("10 and 20 make: " + increasingSum(10, 20));
13:         System.out.println("20 and 10 make: " + increasingSum(20, 10) + " <-- look at that!");
14:     }
15: 
16:     /** Given two positive numbers in increasing order,
17:      *  return their sum if they're divisible,
18:      *  otherwise return y+1.
19:      */
20:     /*@
21:         requires x > 0;
22:         requires y >= x;
23:         ensures \result > y;
24:         pure
25:         //code_java_math // Uncomment to silence overflow/underflow
26:     @*/
27:     public static int increasingSum(final int x, final int y) {
28:         assert x <= y : "Args were not in increasing order: " + x + " and " + y;
29: 
30:         // Notice that this code looks "correct" but suffers from overflow errors
31:         // and if assertions are turned off, can produce a divide-by-zero error.
32:         // Our JML specs can catch all of that
33: 
34:         return y % x == 0 ? x + y : y + 1;
35:     }
36: }

(End of file - total 36 lines)
</content>
```

---

## Assistant (Plan · gpt-5.2 · 35.0s)

_Thinking:_



Proceeding with Option 1: `long` ticks.

Extended validation plan (JUnit 5 + jqwik), focused on invariants/constraints and full state-machine validation.

**JUnit 5 unit tests (deterministic, example-based)**
Create `SimulationTest` (and small helper state types) that asserts:

- Construction
  - `create(null)` throws `NullPointerException`
  - `create(s0)` => `now()==0`, `state()==s0`, `isEmpty()==true`, `peekNext().isEmpty()`

- Scheduling constraints
  - `scheduleAt(time < now, action)` throws `IllegalArgumentException`
  - `scheduleIn(delay < 0, action)` throws `IllegalArgumentException`
  - `scheduleAt(time, null)` / `scheduleIn(delay, null)` throws `NullPointerException`
  - `scheduleIn(delay, action)` uses `Math.addExact(now, delay)`; overflow throws `ArithmeticException` (test using `now` near `Long.MAX_VALUE` via controlled setup or direct call path if available)

- Ordering + tie-breaking
  - Events execute by increasing `time`
  - Same `time` executes FIFO by scheduling order (sequence tie-breaker)
  - `peekNext()` returns the same event repeatedly until a `step()`; does not mutate `now/state/queue`

- Stepping invariants
  - `step()` on empty queue throws `IllegalStateException` (or the chosen exception type; document and test)
  - After each `step()`:
    - `now` is non-decreasing
    - `now == executedEvent.time`
    - `state` is exactly `action.apply(previousState)`
    - Queue size decreases by exactly 1

- Run semantics
  - `run()` executes until empty; `isEmpty()==true` after
  - `runUntilTime(tEnd < now)` throws `IllegalArgumentException`
  - `runUntilTime(tEnd)` executes exactly events with `time <= tEnd`; next event (if any) has `time > tEnd`; `now <= tEnd` unless queue initially empty
  - Determinism smoke test: same initial state + same scheduling calls => identical final state and step count

**jqwik property-based tests (model-based state machine)**
Use jqwik “stateful / model-based” testing to validate the full DES state machine by comparing the real engine (SUT) against a simple reference model that encodes the spec.

- Reference model (pure, minimal)
  - Fields: `long now`, `S state`, `long nextSeq`, and an ordered structure of pending events:
    - e.g., `TreeMap<Long, ArrayList<RefEvent>>` where `RefEvent` includes `seq` + `UnaryOperator<S>`
    - Ordering: smallest time first; within time, smallest `seq` first (FIFO scheduling order)
  - Implement pure operations mirroring the public API: `scheduleAt`, `scheduleIn`, `peekNext`, `step`, `runUntilTime`
  - Enforce the same preconditions and exception behavior

- Stateful action set (generated sequences)
  Generate sequences of actions, each action constrained by the model’s current state so preconditions hold (except in dedicated “invalid input” properties):
  - `ScheduleAt`: choose `time >= model.now` and a small action (e.g., add an `int delta` to state)
  - `ScheduleIn`: choose `delay >= 0` avoiding overflow (`now + delay` within range) for “valid” sequences
  - `PeekNext`: always allowed
  - `Step`: only when pending queue non-empty
  - `RunUntilTime`: choose `tEnd >= model.now` (and optionally beyond next event time sometimes)
  - (Optional) `Run`: can be represented as `RunUntilTime(Long.MAX_VALUE)` if that matches semantics

- Properties to assert after every action (SUT vs reference)
  - `sut.now() == model.now`
  - `sut.state().equals(model.state)` (pick an immutable test state with structural equality)
  - `sut.isEmpty() == model.queueEmpty`
  - `sut.peekNext()` presence matches; if present, `time` and `EventId` ordering behavior matches spec (at least `time`, and FIFO at same-time via observed execution order)
  - If an action is `step()`:
    - executed event times are identical
    - post-step `now` monotone
    - (If you expose `StepResult`) verify `previousState/newState` align with both SUT + model

- Additional jqwik properties (non-stateful, focused)
  - “FIFO at same time”: generate `n` actions scheduled at identical `time`, assert stepping yields effects in schedule order
  - “No events in the past”: for any valid sequence, `peekNext().time >= now` (or empty)
  - “Determinism”: run the same generated schedule twice (fresh sims) => identical final state and identical trace of `now` values

- Negative / constraint properties (separate)
  - Generate invalid inputs to assert the exact exception types/messages policy you choose:
    - `scheduleAt(time < now)` always throws
    - `scheduleIn(delay < 0)` always throws
    - overflow in `scheduleIn` throws `ArithmeticException`

**Practical testing setup in this repo**
- Your `pom.xml` already includes JUnit 5 + jqwik and surefire, so the plan is just to add:
  - JUnit tests under `src/test/java/...`
  - jqwik properties either in the same test class or a dedicated `SimulationPropertiesTest`
- Package naming: to fit the existing repo (`com.kevel`), place library code under something like `com.kevel.des` and tests under `com.kevel.des`.

If you want, I can also spell out the exact reference-model data structure and the jqwik `Action`/sequence scaffolding (jqwik stateful API) so it’s ready to implement with minimal iteration.

---

