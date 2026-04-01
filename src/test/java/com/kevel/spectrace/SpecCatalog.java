package com.kevel.spectrace;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable lookup table for canonical spec identifier definitions.
 *
 * <p>The catalog preserves discovery order so coverage failures are reported deterministically.
 * Mutation is forbidden after construction; callers receive only unmodifiable views.
 *
 * <p>Example:
 *
 * <pre>{@code
 * SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);
 * SpecIdDefinition definition = catalog.definition("TRACE-JUNIT");
 * }</pre>
 */
final class SpecCatalog {

    private final Map<String, SpecIdDefinition> definitions;

    /**
     * Creates an immutable catalog from discovered definitions.
     *
     * <p>Preconditions: {@code definitions} is non-null and already satisfies the uniqueness
     * invariant established by the builder. Postconditions: iteration order is preserved and later
     * callers cannot mutate the backing map.
     *
     * @param definitions identifier-to-definition mapping; must be non-null
     * @throws NullPointerException if {@code definitions} is null
     */
    SpecCatalog(Map<String, SpecIdDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions = Map.copyOf(new LinkedHashMap<>(definitions));
    }

    /**
     * Resolves a definition by identifier.
     *
     * <p>Preconditions: {@code identifier} is non-null. Postconditions: the method returns the
     * matching definition when present and {@code null} otherwise.
     *
     * @param identifier canonical identifier text; must be non-null
     * @return the matching definition, or {@code null} when the identifier is unknown
     * @throws NullPointerException if {@code identifier} is null
     */
    SpecIdDefinition definition(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return definitions.get(identifier);
    }

    /**
     * Returns all definitions in deterministic discovery order.
     *
     * @return unmodifiable definitions view; never null
     */
    Collection<SpecIdDefinition> definitions() {
        return definitions.values();
    }

    /**
     * Returns all known identifier strings in deterministic discovery order.
     *
     * @return unmodifiable identifier set; never null
     */
    Set<String> identifiers() {
        return definitions.keySet();
    }

    /**
     * Indicates whether the catalog has no definitions.
     *
     * @return {@code true} when the catalog is empty
     */
    boolean isEmpty() {
        return definitions.isEmpty();
    }
}
