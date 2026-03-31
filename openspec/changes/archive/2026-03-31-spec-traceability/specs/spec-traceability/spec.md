## ADDED Requirements

### Requirement: Identifier syntax and bracket delimiters
A spec identifier SHALL be an uppercase kebab-style token matching the pattern `[A-Z][A-Z0-9]*(-[A-Z0-9]+)+` (e.g., `BOX-NULL-REJECT`, `SC-NORM-LOWER`). In canonical spec markdown, identifiers SHALL be written inside square brackets (e.g., `[BOX-NULL-REJECT]`). The brackets are required delimiters that mark the identifier for machine extraction; they are not part of the identifier itself. Authors SHOULD place identifiers on `Requirement` or `Scenario` headers. In test source, the bare identifier without brackets SHALL be used in the `@SpecTrace` annotation (e.g., `@SpecTrace({"BOX-NULL-REJECT"})`).

#### Scenario: Bracketed identifier is extracted without brackets
- **WHEN** a canonical spec file contains `[BOX-NULL-REJECT]`
- **THEN** the harness extracts `BOX-NULL-REJECT` as the identifier, not `[BOX-NULL-REJECT]`

#### Scenario: Identifier on a requirement or scenario header is extracted
- **WHEN** a canonical spec file places `[BOX-NULL-REJECT]` on a `Requirement` or `Scenario` header
- **THEN** the harness extracts `BOX-NULL-REJECT` and associates it with that header's requirement or scenario context

#### Scenario: Bare uppercase kebab token without brackets is not extracted
- **WHEN** a canonical spec file contains `BOX-NULL-REJECT` without surrounding brackets
- **THEN** the harness does not extract it as an identifier

#### Scenario: Bracketed token that does not match the identifier pattern is ignored
- **WHEN** a canonical spec file contains a bracketed token like `[some link text]` or `[123]`
- **THEN** the harness does not extract it as an identifier

#### Scenario: Bracketed token inside code formatting is ignored
- **WHEN** a canonical spec file contains a bracketed identifier-looking token inside an inline code span or fenced code block
- **THEN** the harness does not extract it as a canonical identifier

### Requirement: Canonical spec identifier discovery
The traceability harness SHALL discover specification identifiers only from canonical OpenSpec files located at `openspec/**/spec.md`. For each discovered identifier the harness SHALL retain the defining file, line number, and nearest requirement or scenario heading when available. When an identifier is placed on a `Requirement` or `Scenario` header, the harness SHALL retain that same header as the provenance context without including the bracketed identifier token in the stored heading text. The harness SHOULD include the nearest heading in diagnostic messages to help authors locate the requirement context.

#### Scenario: Discover identifiers from canonical spec files
- **WHEN** the project contains bracketed identifiers in files matching `openspec/**/spec.md`
- **THEN** the harness discovers those identifiers and records their defining file and line number

#### Scenario: Ignore non-canonical spec files
- **WHEN** the project contains matching-looking bracketed identifiers outside `openspec/**/spec.md`
- **THEN** the harness does not include those identifiers in the canonical spec catalog

### Requirement: Test methods declare traced identifiers explicitly
Each traced JUnit 5 test method SHALL declare the spec identifier or identifiers it verifies using a method-level `@SpecTrace` annotation that is visible in the test source. Each declared identifier SHALL be the bare identifier (without brackets) and SHALL match the identifier syntax pattern `[A-Z][A-Z0-9]*(-[A-Z0-9]+)+`.

#### Scenario: Ordinary JUnit test declares one identifier
- **WHEN** a JUnit 5 `@Test` method declares a canonical spec identifier via `@SpecTrace`
- **THEN** the harness associates that test method with the declared identifier during execution

#### Scenario: Property test declares multiple identifiers
- **WHEN** a jqwik `@Property` method declares multiple canonical spec identifiers via `@SpecTrace`
- **THEN** the harness associates that property method with each declared identifier during execution

#### Scenario: Malformed identifier in annotation reports a format error
- **WHEN** a traced test method declares an identifier that does not match the uppercase kebab pattern (e.g., lowercase `box-null-reject`, contains spaces, or is empty)
- **THEN** the harness SHALL fail the declaring test method with a format-error diagnostic distinct from an unknown-identifier error, reporting the expected pattern

#### Scenario: Empty annotation array is an error
- **WHEN** a test method carries `@SpecTrace({})` with an empty identifier array
- **THEN** the harness SHALL fail the declaring test method with an error indicating that at least one identifier is required

### Requirement: Tests without traceability declarations are unaffected
Tests that do not carry a `@SpecTrace` annotation SHALL NOT participate in traceability validation or coverage accounting. They SHALL execute normally without harness interference.

#### Scenario: Unannotated test runs without traceability checks
- **WHEN** a `@Test` or `@Property` method does not carry a `@SpecTrace` annotation
- **THEN** the harness does not validate, record, or fail that method for traceability purposes

### Requirement: Unknown identifiers fail the declaring test method
IF a traced test declares an identifier that is not present in the canonical spec catalog, THEN the harness SHALL fail the declaring test method. Other test methods in the same run SHALL continue executing normally.

#### Scenario: Unknown identifier fails the declaring method and reports diagnostics
- **WHEN** a traced test method declares an identifier not present in any canonical spec file
- **THEN** the harness fails that test method and reports the test method name and the unknown identifier
- **AND** other test methods in the run continue executing

### Requirement: Cross-file duplicate identifiers are rejected
IF the same identifier is defined in more than one canonical spec file, THEN the harness SHALL fail catalog building and report an error before any traceability validation proceeds. Reuse of the same identifier within a single spec file is intentionally permitted and SHALL NOT be treated as an error.

#### Scenario: Duplicate identifiers across spec files fail catalog building
- **WHEN** two canonical spec files define the same identifier
- **THEN** the harness fails catalog building before any traced tests execute and reports both defining files with provenance

#### Scenario: Same identifier repeated within one spec file is permitted
- **WHEN** a single canonical spec file contains the same bracketed identifier on multiple lines
- **THEN** the harness records the first occurrence for provenance and does not report a duplicate error

### Requirement: Provenance-aware diagnostics identify definition locations
WHEN the harness reports an uncovered identifier, a catalog error, or an unknown-identifier failure, it SHALL include provenance for the relevant identifier, including the canonical spec file and line number where the identifier was defined.

#### Scenario: Uncovered identifier reports file and line
- **WHEN** coverage enforcement is enabled and a canonical spec identifier is not declared by any traced test in the run
- **THEN** the harness reports the identifier together with its defining file and line number

#### Scenario: Duplicate identifier error reports both provenance locations
- **WHEN** duplicate canonical identifiers are discovered across files
- **THEN** the harness reports the identifier together with each defining file and line number

#### Scenario: Unknown identifier error includes provenance when available
- **WHEN** a traced test declares an identifier that is unknown
- **THEN** the harness reports the test method and identifier, and if the catalog is non-empty, the diagnostic does not include provenance for the unknown identifier (since it has no definition)

### Requirement: Empty catalog behavior
WHEN no canonical spec files exist under `openspec/**/spec.md` or the canonical files contain no valid bracketed identifiers, the catalog SHALL be empty. Traced tests referencing identifiers against an empty catalog SHALL fail as unknown references. Coverage enforcement against an empty catalog SHALL trivially pass because there are no identifiers to cover.

#### Scenario: Traced test fails against empty catalog
- **WHEN** no canonical spec files exist and a traced test declares an identifier
- **THEN** the harness fails the declaring test method with an unknown-identifier error

#### Scenario: Coverage enforcement on empty catalog passes
- **WHEN** no canonical spec files exist and `spec.trace.coverage` is `true`
- **THEN** the harness reports that coverage trivially passes with zero identifiers to cover

### Requirement: Coverage enforcement is controlled independently from reference validation
The harness SHALL always validate traced test references against the full canonical spec catalog. The harness SHALL enforce uncovered-spec coverage only when the `spec.trace.coverage` system property is `true`.

#### Scenario: Coverage disabled still validates references
- **WHEN** `spec.trace.coverage` is unset or `false`
- **THEN** traced tests still fail for unknown identifiers, malformed identifiers, and duplicate canonical identifiers

#### Scenario: Coverage enabled reports uncovered identifiers
- **WHEN** `spec.trace.coverage` is `true`
- **THEN** the harness fails the run if any canonical spec identifier is not declared by at least one traced test

### Requirement: Reference validation uses the full canonical catalog during subset runs
WHILE executing a subset of test classes or methods, the harness SHALL validate traced identifiers against the full set of canonical spec files in the project.

#### Scenario: Single test class run still validates against all canonical specs
- **WHEN** a developer runs only one traced test class
- **THEN** the harness validates that class's declared identifiers against the full canonical spec catalog

### Requirement: Review-only requirements remain traceable
Requirements that are verified by reasoning rather than executable checks SHALL still be traceable through a passing traced test that includes a comment explaining the reasoning or cites the supporting documentation.

#### Scenario: Review-only requirement uses a traced passing test
- **WHEN** a requirement cannot be verified by an automated assertion
- **THEN** a passing traced test with an explanatory comment can satisfy the traceability relationship for that identifier
