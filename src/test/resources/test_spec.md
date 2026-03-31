# Evidence Script Test Spec

This markdown file is a passing integration fixture for `scripts/evidence.sh`.
It exercises supported evidence languages, unmatched lines with no assertion comments,
multi-line Java inputs, unsupported-language skipping, and file-wide Alloy collection.

#### Scenario: Java value and unmatched lines
- **WHEN** Java evidence blocks contain a mix of asserted and unmatched lines
- **THEN** unmatched lines execute without requiring an assertion comment

##### Evidence
- Example:

```java
int n = 1;
n + 1; //=> 2
n = n + 4;

String prefix = "alpha";
n; //=> 5
prefix = prefix + "-beta";
prefix; //=> alpha-beta
```

### Alloy Fragment 1

```alloy
sig Person {}

sig Project {
  owner: one Person
}
```

#### Scenario: Java type assertions
- **WHEN** Java assignments use different declaration forms
- **THEN** type assertions succeed and unmatched lines around them remain valid

##### Evidence
- Example:
```java
var count = 3;
count = count + 1;
count = count; //=> type int

String name = "demo";
name = name.trim();
name = name; //=> type String

int score = 9;
score = score + 1;
score = score; //=> type int

final Double ratio = 2.5; //=> type Double
ratio.toString();
```

#### Scenario: Java throws assertions
- **WHEN** Java code throws exceptions in evidence examples
- **THEN** both plain and brace-wrapped throw expectations succeed

##### Evidence
- Example:
```java
String digits = "123";
digits.length();
Integer.parseInt("bad"); //=> throws NumberFormatException

int safe = Integer.parseInt("7");
safe;
Integer.parseInt("NaN"); //=> throws {NumberFormatException}

String done = "ok";
done; //=> ok
```

#### Scenario: Java wildcard assertions
- **WHEN** Java expressions produce any normal value output
- **THEN** wildcard expectations succeed with unmatched lines around them

##### Evidence
- Example:
```java
int seed = 10;
seed + 5; //*
seed = seed * 2;
seed; //=> 20

"abc".toUpperCase(); //*
String tail = "done";
tail = tail + "!";
tail; //=> done!
```

### Alloy Fragment 2

```alloy
pred wellFormed {
  all p: Project | one p.owner
}
```

#### Scenario: Multi-line Java statements and expressions
- **WHEN** a single Java statement spans multiple markdown lines
- **THEN** an assertion comment on the final line still checks the completed input

##### Evidence
- Example:
```java
int seed = 10;

var total =
    seed +
    5; //=> 15

seed = seed + 1;

Integer.parseInt(
    "bad"
); //=> throws NumberFormatException

final
String label = "demo"; //=> type String

(
    label +
    "!"
); //*

seed = seed * 2;
seed; //=> 22
```

#### Scenario: Java parser edge cases that still pass
- **WHEN** evidence lines use parser-adjacent forms
- **THEN** recognized assertions still pass and comment-only markers are ignored

##### Evidence
- Example:
```java
//=> ignored comment-only expectation
//*
int edge = 4;
edge+1;//=> 5
edge = edge + 1;
edge; //=>   5

String combo = "value " + "/" + "*";
combo; //=> value /*

String weird = "//=>type";
weird.length(); //*
```

#### Scenario: JavaScript and TypeScript assertions
- **WHEN** JavaScript and TypeScript evidence blocks use supported aliases
- **THEN** value, type, throw, and wildcard assertions all succeed with unmatched lines mixed in

##### Evidence
- Example:
```js
let a = 1;
a + 2; //=> 3
a = a + 1;
a = a; //=> type Number
JSON.parse("{"); //=> throws SyntaxError
let ok = "done";
ok; //=> done
```

- Example:
```javascript
const greeting = "hi";
greeting.toUpperCase(); //*
const copy = greeting + "!";
copy; //=> hi!
```

- Example:
```ts
let total = 5;
total = total + 7;
total = total; //=> type Number
const state = "warm";
state; //=> warm
```

- Example:
```typescript
const text = "abc";
text.length; //=> 3
const parsed = JSON.parse("true");
parsed; //=> true
```

#### Scenario: Unsupported evidence language is skipped
- **WHEN** an evidence block uses an unsupported language
- **THEN** the block is skipped and later scenarios still run

##### Evidence
- Example:
```python
raise Exception("this should be skipped")
1 / 0
```

#### Scenario: Alloy model fragments
- **WHEN** Alloy code blocks are split across different sections of the file
- **THEN** the combined model remains simple and satisfiable

##### Evidence
- Example:
```java
int finalCheck = 99;
finalCheck + 1; //=> 100
finalCheck = finalCheck + 1;
finalCheck; //=> 100
```

### Alloy Fragment 3

```alloy
run { wellFormed and some Person and some Project } for 3
```
