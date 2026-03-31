### Requirement: Catalog parser finds bracketed ids [TRACE-JUNIT]
- **WHEN** canonical markdown contains the identifier on a requirement header
- **THEN** it is extracted as a spec identifier

#### Scenario: Property coverage can span files [TRACE-PROPERTY]
- **WHEN** a property references the scenario identifier
- **THEN** the first scenario identifier remains available

#### Scenario: Review-only coverage stays traceable [TRACE-REVIEW-ONLY]
- **WHEN** a review-only test references the scenario identifier
- **THEN** the second scenario identifier remains available

### Requirement: Uncovered coverage is reported [TRACE-UNUSED]
- **WHEN** the requirement identifier has no traced test
- **THEN** coverage reports its provenance

`[TRACE-IGNORED-INLINE-CODE]`

```java
String ignored = "[TRACE-IGNORED-FENCED-CODE]";
```
