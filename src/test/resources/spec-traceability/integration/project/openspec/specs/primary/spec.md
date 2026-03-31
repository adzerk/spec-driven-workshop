### Requirement: Traced JUnit methods validate known identifiers [TRACE-JUNIT]
- **WHEN** a test declares the requirement identifier
- **THEN** the harness allows it to run

### Requirement: Property methods can trace multiple identifiers [TRACE-PROPERTY]
- **WHEN** a property declares the requirement identifier
- **THEN** the harness records it during execution

### Requirement: Uncovered coverage is reported [TRACE-UNUSED]
- **WHEN** the requirement identifier is not declared by any traced test
- **THEN** coverage reports its provenance

### Requirement: Review-only requirements remain traceable [TRACE-REVIEW-ONLY]
- **WHEN** a review-only check uses the requirement identifier
- **THEN** a passing traced test can document the reasoning
