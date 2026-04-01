package com.kevel.spectrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link SpecCatalogBuilder} parsing and duplicate handling.
 *
 * <p>These tests exercise the parser directly so failures isolate catalog semantics from the JUnit
 * runtime integration.
 *
 * <p>Example:
 *
 * <pre>{@code
 * SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);
 * assertNotNull(catalog);
 * }</pre>
 */
class SpecCatalogBuilderTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that only canonical {@code spec.md} files contribute identifiers and parser filters
     * out fenced or inline-code examples.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void discoversBracketedIdentifiersFromCanonicalSpecFilesOnly() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecCatalogBuilderTest.class, "spec-traceability/catalog/valid", tempDir.resolve("project"));

        SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);

        assertEquals(5, catalog.identifiers().size());
        assertTrue(catalog.identifiers().contains("TRACE-JUNIT"));
        assertTrue(catalog.identifiers().contains("TRACE-PROPERTY"));
        assertTrue(catalog.identifiers().contains("TRACE-REVIEW-ONLY"));
        assertTrue(catalog.identifiers().contains("TRACE-SECONDARY"));
        assertTrue(catalog.identifiers().contains("TRACE-UNUSED"));
        assertFalse(catalog.identifiers().contains("TRACE-IGNORED-NONCANONICAL"));
        assertFalse(catalog.identifiers().contains("TRACE-IGNORED-INLINE-CODE"));
        assertFalse(catalog.identifiers().contains("TRACE-IGNORED-FENCED-CODE"));
        assertEquals(
                "Requirement: Catalog parser finds bracketed ids",
                catalog.definition("TRACE-JUNIT").headingContext());
        assertEquals(
                "Requirement: Secondary canonical files participate",
                catalog.definition("TRACE-SECONDARY").headingContext());
        assertEquals(1, catalog.definition("TRACE-JUNIT").lineNumber());
        assertEquals(5, catalog.definition("TRACE-PROPERTY").lineNumber());
        assertEquals(9, catalog.definition("TRACE-REVIEW-ONLY").lineNumber());
    }

    /**
     * Verifies that within-file duplicates keep first provenance instead of inflating obligations.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void withinFileDuplicatesKeepTheFirstOccurrence() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecCatalogBuilderTest.class, "spec-traceability/catalog/within-file", tempDir.resolve("project"));

        SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);

        assertEquals(1, catalog.identifiers().size());
        assertEquals(1, catalog.definition("TRACE-DUPLICATE").lineNumber());
    }

    /**
     * Verifies that cross-file duplicates return a provenance-rich catalog error.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void crossFileDuplicatesFailWithProvenance() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecCatalogBuilderTest.class, "spec-traceability/catalog/duplicate", tempDir.resolve("project"));

        SpecCatalogException exception =
                assertThrows(SpecCatalogException.class, () -> SpecCatalogBuilder.build(projectRoot));
        assertTrue(exception.getMessage().contains("TRACE-DUPLICATE"));
        assertTrue(exception.getMessage().contains("openspec/specs/alpha/spec.md:1"));
        assertTrue(exception.getMessage().contains("openspec/specs/beta/spec.md:1"));
    }

    /**
     * Verifies that the absence of canonical spec files yields an empty catalog rather than a
     * failure.
     */
    @Test
    void emptyCatalogIsAllowedWhenNoCanonicalSpecsExist() {
        SpecCatalog catalog = SpecCatalogBuilder.build(tempDir);

        assertTrue(catalog.isEmpty());
    }

    /**
     * Verifies that null project roots are rejected at the boundary.
     */
    @Test
    void nullProjectRootFailsFast() {
        assertThrows(NullPointerException.class, () -> SpecCatalogBuilder.build(null));
    }
}
