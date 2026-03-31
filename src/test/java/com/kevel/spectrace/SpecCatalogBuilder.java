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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpecCatalogBuilder {

    static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+");

    private static final Pattern BRACKETED_IDENTIFIER_PATTERN =
            Pattern.compile("\\[([A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+)\\]");
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^\\s*#{3,4}\\s+(Requirement|Scenario):\\s+(.+?)\\s*$");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`[^`]*`");
    private static final Pattern MULTISPACE_PATTERN = Pattern.compile("\\s{2,}");

    private SpecCatalogBuilder() {}

    static SpecCatalog build(Path projectRoot) {
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        Path openspecRoot = normalizedRoot.resolve("openspec");
        if (!Files.isDirectory(openspecRoot)) {
            return new SpecCatalog(Map.of());
        }

        List<Path> specFiles;
        try (var paths = Files.walk(openspecRoot)) {
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

    private static void parseFile(
            Path projectRoot,
            Path specFile,
            Map<String, SpecIdDefinition> definitions,
            Map<String, List<SpecIdDefinition>> conflicts) {
        List<String> lines;
        try {
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
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            if (headingMatcher.matches()) {
                nearestHeading =
                        "%s: %s".formatted(headingMatcher.group(1), sanitizeHeadingText(headingMatcher.group(2)));
            }

            String searchableLine = INLINE_CODE_PATTERN.matcher(line).replaceAll("`");
            Matcher identifierMatcher = BRACKETED_IDENTIFIER_PATTERN.matcher(searchableLine);
            while (identifierMatcher.find()) {
                String identifier = identifierMatcher.group(1);
                if (!seenInFile.add(identifier)) {
                    continue;
                }

                SpecIdDefinition definition = new SpecIdDefinition(identifier, relativePath, index + 1, nearestHeading);
                SpecIdDefinition existing = definitions.get(identifier);
                if (existing == null) {
                    definitions.put(identifier, definition);
                } else if (!existing.definingPath().equals(definition.definingPath())) {
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

    private static String formatConflictMessage(Map<String, List<SpecIdDefinition>> conflicts) {
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

    private static String sanitizeHeadingText(String headingText) {
        String withoutIdentifiers =
                BRACKETED_IDENTIFIER_PATTERN.matcher(headingText).replaceAll("").trim();
        return MULTISPACE_PATTERN.matcher(withoutIdentifiers).replaceAll(" ");
    }
}
