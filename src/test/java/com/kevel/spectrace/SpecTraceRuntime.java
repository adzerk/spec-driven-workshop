package com.kevel.spectrace;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Shared runtime for spec-trace validation and coverage enforcement.
 *
 * <p>Ownership model: this class is the single shared owner of catalog caching and exercised-id
 * tracking for a launcher session. Synchronization discipline: {@link #LOCK} guards {@link
 * #catalogRoot}, {@link #catalog}, and {@link #exercisedIdentifiers}. No caller may read or mutate
 * those fields outside the synchronized region.
 *
 * <p>Named invariants:
 * <ul>
 *   <li>{@code catalog == null} iff the catalog has not yet been loaded for the current root.
 *   <li>When {@code catalog != null}, {@code catalogRoot} is non-null and names the root used to
 *       build that catalog.
 *   <li>{@code exercisedIdentifiers} contains only identifiers that have already been validated
 *       against the current catalog.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * SpecTraceRuntime.resetExecution();
 * SpecTraceRuntime.validateAndRecord(testMethod);
 * SpecTraceRuntime.assertCoverageSatisfied();
 * }</pre>
 */
final class SpecTraceRuntime {

    static final String COVERAGE_PROPERTY = "spec.trace.coverage";
    static final String ROOT_PROPERTY = "spec.trace.root";

    private static final Object LOCK = new Object();

    private static Path catalogRoot;
    private static SpecCatalog catalog;
    private static final Set<String> exercisedIdentifiers = new LinkedHashSet<>();

    private SpecTraceRuntime() {}

    /**
     * Clears per-run execution state.
     *
     * <p>Postconditions: the exercised identifier set is empty. The catalog cache is intentionally
     * retained so repeated validations in the same launcher session do not rescan the filesystem.
     */
    static void resetExecution() {
        synchronized (LOCK) {
            exercisedIdentifiers.clear();
        }
    }

    /**
     * Validates a traced method and records its exercised identifiers.
     *
     * <p>Preconditions: {@code method} is non-null. Postconditions: if the method carries {@link
     * SpecTrace}, every identifier is syntactically valid, present in the current catalog, and then
     * recorded in the exercised-id set.
     *
     * @param method reflected test method; must be non-null
     * @throws NullPointerException if {@code method} is null
     * @throws AssertionError if identifiers are malformed, empty, or absent from the catalog
     * @throws SpecCatalogException if the canonical catalog has structural conflicts
     */
    static void validateAndRecord(Method method) {
        Objects.requireNonNull(method, "method");
        SpecTrace annotation = method.getAnnotation(SpecTrace.class);
        if (annotation == null) {
            return;
        }

        String methodName = "%s#%s".formatted(method.getDeclaringClass().getName(), method.getName());
        String[] identifiers = annotation.value();
        if (identifiers.length == 0) {
            throw new AssertionError(
                    "Empty @SpecTrace declaration on %s; at least one identifier is required".formatted(methodName));
        }

        for (String identifier : identifiers) {
            if (!SpecCatalogBuilder.IDENTIFIER_PATTERN.matcher(identifier).matches()) {
                throw new AssertionError("Malformed @SpecTrace identifier '%s' on %s; expected pattern %s"
                        .formatted(identifier, methodName, SpecCatalogBuilder.IDENTIFIER_PATTERN.pattern()));
            }
        }

        SpecCatalog resolvedCatalog = catalog();
        for (String identifier : identifiers) {
            if (resolvedCatalog.definition(identifier) == null) {
                throw new AssertionError(
                        "Unknown @SpecTrace identifier '%s' declared by %s".formatted(identifier, methodName));
            }
        }

        synchronized (LOCK) {
            for (String identifier : identifiers) {
                exercisedIdentifiers.add(identifier);
            }
        }
    }

    /**
     * Fails when coverage enforcement is enabled and some canonical identifiers were not exercised.
     *
     * @throws AssertionError if coverage is enabled and uncovered identifiers remain
     * @throws SpecCatalogException if the canonical catalog has structural conflicts
     */
    static void assertCoverageSatisfied() {
        if (!coverageEnabled()) {
            return;
        }

        SpecCatalog resolvedCatalog = catalog();
        List<SpecIdDefinition> uncovered = new ArrayList<>();
        synchronized (LOCK) {
            for (SpecIdDefinition definition : resolvedCatalog.definitions()) {
                if (!exercisedIdentifiers.contains(definition.identifier())) {
                    uncovered.add(definition);
                }
            }
        }

        if (!uncovered.isEmpty()) {
            StringBuilder builder = new StringBuilder("Uncovered canonical spec identifiers were found:");
            for (SpecIdDefinition definition : uncovered) {
                builder.append(System.lineSeparator())
                        .append("- ")
                        .append(definition.identifier())
                        .append(" defined at ")
                        .append(definition.formatProvenance());
            }
            throw new AssertionError(builder.toString());
        }
    }

    /**
     * Resolves the cached catalog for the current project root.
     *
     * <p>Postconditions: the returned catalog matches the normalized root from {@link
     * #projectRoot()}. Cache misses rebuild the catalog exactly once per distinct root.
     *
     * @return catalog for the current project root; never null
     * @throws SpecCatalogException if the canonical catalog has structural conflicts
     */
    static SpecCatalog catalog() {
        synchronized (LOCK) {
            Path root = projectRoot();
            if (catalog == null || !root.equals(catalogRoot)) {
                catalog = SpecCatalogBuilder.build(root);
                catalogRoot = root;
            }
            return catalog;
        }
    }

    /**
     * Resolves the normalized project root, optionally using a test override property.
     *
     * @return normalized absolute project root; never null
     */
    static Path projectRoot() {
        String override = System.getProperty(ROOT_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    /**
     * Returns whether end-of-run coverage enforcement is enabled.
     *
     * @return {@code true} when {@value #COVERAGE_PROPERTY} is exactly {@code "true"}
     */
    private static boolean coverageEnabled() {
        return "true".equals(System.getProperty(COVERAGE_PROPERTY));
    }
}
