# Spec Traceability

## Overview

A protocol for bidirectional traceability between OpenSpec requirements and tests. Each project implements test-harness integration in its own language and framework. The protocol defines what the integration must enforce, not how it is implemented.

## Requirements

### Identifier Uniqueness
Each spec requirement SHALL have a machine-readable identifier, in a uppercase kebab format (e.g., `SC-NORM-LOWER`) chosen by the project, that is unique across all spec files in the project.

### Author-Time Visibility
Spec identifiers SHALL be visible in both spec source (where defined) and test source (where referenced), so that traceability is apparent to authors reading either artifact.

### Test Reference Declaration
Each traced test SHALL declare which spec identifier(s) it verifies.

### Unknown Reference Detection
WHEN a test declares a spec identifier not found in any spec file in the project, the test harness SHALL report an error.

### Cross-File Duplicate Detection
IF the same identifier is defined in more than one spec file, THEN the test harness SHALL report an error. Reuse of the same identifier within a single spec file is permitted.

### Bijective Coverage
WHEN running the full test suite, each spec identifier SHALL be declared by at least one test.

## Recommendations

### Assertion-Level Placement
Identifiers SHOULD be placed at the level of the individual spec assertion or Scenario, not at the level of a grouping heading. A heading that contains five independently testable bullets SHOULD have five identifiers, not one.

### Separation of Validation and Coverage
Unknown-reference detection and coverage enforcement SHOULD be independently controllable, so that disabling coverage during subset runs does not disable reference validation.

### Parsing Scope
The test harness SHOULD recognize identifiers regardless of their position in the spec document structure — headings, bullets, and inline text.

### Subset Validation
WHILE running a subset of test files, the test harness SHOULD validate declared identifiers against the full set of spec files in the project.

### Provenance
WHEN reporting an uncovered or unknown identifier, the test harness SHOULD include the file and line where the identifier is defined.

### Descriptive Identifiers
Identifiers SHOULD be descriptive of the claim they represent (e.g., `SC-NORM-LOWER`) rather than purely numeric (e.g., `SC-013`), so that the claim is apparent without a lookup.

### Review-Only Requirements
Requirements that cannot be verified by automated tests SHOULD be covered by a phony passing test with a comment explaining why the requirement is verified by reasoning, and document or cite the reasoning within the comment.

