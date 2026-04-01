package com.kevel.spectrace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a canonical spec catalog by scanning canonical {@code spec.md} files under
 * {@code openspec}.
 *
 * <p>The builder strips inline-code spans, ignores fenced code blocks, tracks the nearest
 * requirement or scenario heading, and rejects cross-file duplicate identifiers with provenance-rich
 * diagnostics.
 *
 * <p>Example:
 *
 * <pre>{@code
 * SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);
 * }</pre>
 */
final class SpecCatalogBuilder {

    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+");

    private static final int MAX_SPEC_SCAN_DEPTH = 8;
    private static final long MAX_SPEC_FILE_BYTES = 1_048_576;
    private static final Pattern BRACKETED_IDENTIFIER_PATTERN =
            Pattern.compile("\\[([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+)\\]");
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^\\s*#{3,4}\\s+(Requirement|Scenario):\\s+(.+?)\\s*$");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`]*`");
    private static final Pattern MULTISPACE_PATTERN = Pattern.compile("\\s{2,}");

    private SpecCatalogBuilder() {}

    /**
     * Builds a catalog from canonical spec files under the given project root.
     *
     * <p>Preconditions: {@code projectRoot} is non-null. Postconditions: the returned catalog is
     * immutable and contains at most one definition per identifier.
     *
     * @param projectRoot project root containing an optional {@code openspec} directory; must be
     *     non-null
     * @return immutable catalog of canonical spec definitions
     * @throws NullPointerException if {@code projectRoot} is null
     * @throws SpecCatalogException if duplicate identifiers are defined across canonical spec files
     * @throws UncheckedIOException if directory scanning or file reading fails unexpectedly
     */
    static SpecCatalog build(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path openspecRoot = normalizedRoot.resolve("openspec");
        if (!Files.isDirectory(openspecRoot)) {
            return new SpecCatalog(Map.of());
        }

        List<Path> specFiles;
        try (var paths = Files.walk(openspecRoot, MAX_SPEC_SCAN_DEPTH)) {
            specFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("spec.md"))
                    .sorted(Comparator.comparing(
                            path -> normalizedRoot.relativize(path).toString()))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to scan canonical spec files under " + openspecRoot, exception);
        }

        Map<String, SpecIdDefinition> definitions = new LinkedHashMap<>();
        Map<String, List<SpecIdDefinition>> conflicts = new LinkedHashMap<>();

        for (Path specFile : specFiles) {
            parseFile(normalizedRoot, specFile, definitions, conflicts);
        }

        if (!conflicts.isEmpty()) {
            throw new SpecCatalogException(formatConflictMessage(conflicts));
        }

        return new SpecCatalog(definitions);
    }

    /**
     * Parses a single canonical spec file into the shared catalog state.
     *
     * @param projectRoot normalized project root; must be non-null
     * @param specFile spec file to parse; must be non-null
     * @param definitions mutable map receiving first definitions by identifier; must be non-null
     * @param conflicts mutable map receiving cross-file duplicates; must be non-null
     */
    private static void parseFile(
            Path projectRoot,
            Path specFile,
            Map<String, SpecIdDefinition> definitions,
            Map<String, List<SpecIdDefinition>> conflicts) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(specFile, "specFile");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(conflicts, "conflicts");
        List<String> lines;
        try {
            long specFileBytes = Files.size(specFile);
            if (specFileBytes > MAX_SPEC_FILE_BYTES) {
                throw new IllegalStateException("Canonical spec file exceeds size bound of %d bytes: %s"
                        .formatted(MAX_SPEC_FILE_BYTES, specFile));
            }
            lines = Files.readAllLines(specFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read canonical spec file " + specFile, exception);
        }

        String relativePath = projectRoot
                .relativize(specFile)
                .toString()
                .replace(specFile.getFileSystem().getSeparator(), "/");
        String nearestHeading = null;
        LinkedHashSet<String> seenInFile = new LinkedHashSet<>();
        boolean inFence = false;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                // Fence state is tracked first so bracket tokens inside example code never become
                // canonical identifiers.
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                // Heading context is sanitized once at discovery time so later diagnostics do not
                // repeat bracket tokens that are already reported separately.
                nearestHeading =
                        "%s: %s".formatted(headingMatcher.group(1), sanitizeHeadingText(headingMatcher.group(2)));
            }

            // Inline code spans are replaced before identifier matching so documentation examples do
            // not silently become real coverage obligations.
            String searchableLine = INLINE_CODE_PATTERN.matcher(line).replaceAll("`");
            Matcher identifierMatcher = BRACKETED_IDENTIFIER_PATTERN.matcher(searchableLine);
            while (identifierMatcher.find()) {
                String identifier = identifierMatcher.group(1);
                if (!seenInFile.add(identifier)) {
                    // Within a single file we keep the first occurrence because that gives stable,
                    // minimal provenance and avoids duplicate coverage obligations.
                    continue;
                }

                SpecIdDefinition definition = new SpecIdDefinition(identifier, relativePath, index + 1, nearestHeading);
                SpecIdDefinition existing = definitions.get(identifier);
                if (existing == null) {
                    definitions.put(identifier, definition);
                    continue;
                }
                if (!existing.definingPath().equals(definition.definingPath())) {
                    conflicts.computeIfAbsent(identifier, ignored -> new ArrayList<>(List.of(existing)));
                    List<SpecIdDefinition> duplicates = conflicts.get(identifier);
                    if (duplicates.stream()
                            .noneMatch(existingDefinition ->
                                    existingDefinition.definingPath().equals(definition.definingPath())
                                            && existingDefinition.lineNumber() == definition.lineNumber())) {
                        duplicates.add(definition);
                    }
                }
            }
        }
    }

    /**
     * Formats duplicate-definition diagnostics.
     *
     * @param conflicts identifier-to-provenance map; must be non-null
     * @return human-readable failure message suitable for {@link SpecCatalogException}
     */
    private static String formatConflictMessage(Map<String, List<SpecIdDefinition>> conflicts) {
        Objects.requireNonNull(conflicts, "conflicts");
        StringBuilder builder = new StringBuilder("Duplicate canonical spec identifiers were found across files:");
        conflicts.forEach((identifier, definitions) -> {
            builder.append(System.lineSeparator())
                    .append("- ")
                    .append(identifier)
                    .append(':');
            definitions.forEach(definition ->
                    builder.append(System.lineSeparator()).append("  - ").append(definition.formatProvenance()));
        });
        return builder.toString();
    }

    /**
     * Removes bracketed identifiers from heading text and normalizes whitespace.
     *
     * @param headingText raw heading text; must be non-null
     * @return sanitized heading text with repeated whitespace collapsed
     */
    private static String sanitizeHeadingText(String headingText) {
        Objects.requireNonNull(headingText, "headingText");
        String withoutIdentifiers =
                BRACKETED_IDENTIFIER_PATTERN.matcher(headingText).replaceAll("").trim();
        return MULTISPACE_PATTERN.matcher(withoutIdentifiers).replaceAll(" ");
    }
}
