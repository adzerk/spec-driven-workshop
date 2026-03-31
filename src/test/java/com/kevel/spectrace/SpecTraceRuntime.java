package com.kevel.spectrace;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SpecTraceRuntime {

    static final String COVERAGE_PROPERTY = "spec.trace.coverage";
    static final String ROOT_PROPERTY = "spec.trace.root";

    private static final Object LOCK = new Object();

    private static Path catalogRoot;
    private static SpecCatalog catalog;
    private static final Set<String> exercisedIdentifiers = new LinkedHashSet<>();

    private SpecTraceRuntime() {}

    static void resetExecution() {
        synchronized (LOCK) {
            exercisedIdentifiers.clear();
        }
    }

    static void validateAndRecord(Method method) {
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

    static Path projectRoot() {
        String override = System.getProperty(ROOT_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static boolean coverageEnabled() {
        return "true".equals(System.getProperty(COVERAGE_PROPERTY));
    }
}
