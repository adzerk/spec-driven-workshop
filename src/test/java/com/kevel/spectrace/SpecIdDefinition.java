package com.kevel.spectrace;

import java.util.Objects;

/**
 * Records the provenance of a canonical spec identifier.
 *
 * <p>The record is immutable and safe to share across the test harness. It preserves the
 * identifier text together with the relative file path, 1-based line number, and nearest sanitized
 * heading context used in diagnostics.
 *
 * <p>Example:
 *
 * <pre>{@code
 * SpecIdDefinition definition =
 *         new SpecIdDefinition(
 *                 "TRACE-JUNIT",
 *                 "openspec/specs/primary/spec.md",
 *                 5,
 *                 "Requirement: Jupiter methods validate known identifiers");
 * String message = definition.formatProvenance();
 * }</pre>
 *
 * @param identifier canonical identifier text; must be non-null and non-blank
 * @param definingPath project-relative path to the defining spec file; must be non-null and
 *     non-blank
 * @param lineNumber 1-based line number in the defining file; must be positive
 * @param headingContext nearest sanitized heading text, or {@code null} when no heading applies
 */
record SpecIdDefinition(String identifier, String definingPath, int lineNumber, String headingContext) {

    /**
     * Validates the record invariants.
     *
     * <p>Preconditions: callers provide non-null identifier and path values. Postconditions: every
     * constructed instance has a positive line number and non-blank identifier/path fields.
     *
     * @throws NullPointerException if {@code identifier} or {@code definingPath} is null
     * @throws IllegalArgumentException if the identifier or path is blank, or if the line number is
     *     not positive
     */
    SpecIdDefinition {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(definingPath, "definingPath");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        if (definingPath.isBlank()) {
            throw new IllegalArgumentException("definingPath must not be blank");
        }
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("lineNumber must be positive");
        }
    }

    /**
     * Formats provenance for human-readable failure messages.
     *
     * <p>Postconditions: the returned string is non-null, includes the file path and line number,
     * and appends the heading context when one is available.
     *
     * @return provenance in {@code path:line} or {@code path:line (heading)} form
     */
    String formatProvenance() {
        if (headingContext == null || headingContext.isBlank()) {
            return "%s:%d".formatted(definingPath, lineNumber);
        }
        return "%s:%d (%s)".formatted(definingPath, lineNumber, headingContext);
    }
}
