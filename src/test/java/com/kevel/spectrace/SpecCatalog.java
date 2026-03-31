package com.kevel.spectrace;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class SpecCatalog {

    private final Map<String, SpecIdDefinition> definitions;

    SpecCatalog(Map<String, SpecIdDefinition> definitions) {
        this.definitions = Map.copyOf(new LinkedHashMap<>(definitions));
    }

    SpecIdDefinition definition(String identifier) {
        return definitions.get(identifier);
    }

    Collection<SpecIdDefinition> definitions() {
        return definitions.values();
    }

    Set<String> identifiers() {
        return definitions.keySet();
    }

    boolean isEmpty() {
        return definitions.isEmpty();
    }
}
