package com.kevel.spectrace;

import java.util.Objects;

record SpecIdDefinition(String identifier, String definingPath, int lineNumber, String headingContext) {

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

    String formatProvenance() {
        if (headingContext == null || headingContext.isBlank()) {
            return "%s:%d".formatted(definingPath, lineNumber);
        }
        return "%s:%d (%s)".formatted(definingPath, lineNumber, headingContext);
    }
}
