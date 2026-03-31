package com.kevel.spectrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecCatalogBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void discoversBracketedIdentifiersFromCanonicalSpecFilesOnly() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/catalog/valid", tempDir.resolve("project"));

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

    @Test
    void withinFileDuplicatesKeepTheFirstOccurrence() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/catalog/within-file", tempDir.resolve("project"));

        SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);

        assertEquals(1, catalog.identifiers().size());
        assertEquals(1, catalog.definition("TRACE-DUPLICATE").lineNumber());
    }

    @Test
    void crossFileDuplicatesFailWithProvenance() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/catalog/duplicate", tempDir.resolve("project"));

        SpecCatalogException exception =
                assertThrows(SpecCatalogException.class, () -> SpecCatalogBuilder.build(projectRoot));

        assertTrue(exception.getMessage().contains("TRACE-DUPLICATE"));
        assertTrue(exception.getMessage().contains("openspec/specs/alpha/spec.md:1"));
        assertTrue(exception.getMessage().contains("openspec/specs/beta/spec.md:1"));
    }

    @Test
    void emptyCatalogIsAllowedWhenNoCanonicalSpecsExist() {
        SpecCatalog catalog = SpecCatalogBuilder.build(tempDir);

        assertTrue(catalog.isEmpty());
    }

    private static Path copyResourceDirectory(String resourceName, Path destination)
            throws IOException, URISyntaxException {
        Path source = Path.of(SpecCatalogBuilderTest.class
                .getClassLoader()
                .getResource(resourceName)
                .toURI());
        try (var paths = Files.walk(source)) {
            paths.sorted(Comparator.naturalOrder()).forEach(path -> copyPath(source, destination, path));
        }
        return destination;
    }

    private static void copyPath(Path sourceRoot, Path destinationRoot, Path sourcePath) {
        Path destinationPath =
                destinationRoot.resolve(sourceRoot.relativize(sourcePath).toString());
        try {
            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(destinationPath);
            } else {
                Files.createDirectories(destinationPath.getParent());
                Files.copy(sourcePath, destinationPath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to copy test fixture " + sourcePath, exception);
        }
    }
}
