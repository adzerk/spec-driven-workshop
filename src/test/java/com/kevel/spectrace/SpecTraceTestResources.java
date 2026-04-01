package com.kevel.spectrace;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/**
 * Shared helpers for copying spec-traceability test resource trees into temporary projects.
 *
 * <p>The helpers keep fixture setup deterministic across tests by copying files in natural path
 * order and by creating parent directories before files are copied.
 *
 * <p>Example:
 *
 * <pre>{@code
 * Path projectRoot =
 *         SpecTraceTestResources.copyResourceDirectory(
 *                 SpecTraceIntegrationTest.class,
 *                 "spec-traceability/integration/project",
 *                 tempDir.resolve("project"));
 * }</pre>
 */
final class SpecTraceTestResources {

    private SpecTraceTestResources() {}

    /**
     * Copies a resource directory from the test classpath into a destination directory.
     *
     * <p>Preconditions: {@code owner}, {@code resourceName}, and {@code destination} are non-null,
     * and the named resource exists on the classpath. Postconditions: the returned path is the
     * destination directory containing a byte-for-byte copy of the resource tree.
     *
     * @param owner class whose class loader resolves the resource; must be non-null
     * @param resourceName classpath directory name; must be non-null and non-blank
     * @param destination destination root; must be non-null
     * @return the destination path for fluent setup in tests
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code resourceName} is blank
     * @throws IOException if the resource tree cannot be copied
     * @throws URISyntaxException if the resource URL cannot be converted into a path
     */
    static Path copyResourceDirectory(Class<?> owner, String resourceName, Path destination)
            throws IOException, URISyntaxException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(destination, "destination");
        if (resourceName.isBlank()) {
            throw new IllegalArgumentException("resourceName must not be blank");
        }

        Path source = Path.of(Objects.requireNonNull(
                        owner.getClassLoader().getResource(resourceName),
                        () -> "Missing test resource directory " + resourceName)
                .toURI());
        try (var paths = Files.walk(source)) {
            paths.sorted(Comparator.naturalOrder()).forEach(path -> copyPath(source, destination, path));
        }
        return destination;
    }

    /**
     * Copies a single resource path into the mirrored destination tree.
     *
     * @param sourceRoot root of the source tree; must be non-null
     * @param destinationRoot root of the destination tree; must be non-null
     * @param sourcePath source path to copy; must be non-null
     */
    private static void copyPath(Path sourceRoot, Path destinationRoot, Path sourcePath) {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Objects.requireNonNull(destinationRoot, "destinationRoot");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Path destinationPath =
                destinationRoot.resolve(sourceRoot.relativize(sourcePath).toString());
        try {
            if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(destinationPath);
                return;
            }
            Files.createDirectories(destinationPath.getParent());
            Files.copy(sourcePath, destinationPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to copy test fixture " + sourcePath, exception);
        }
    }
}
