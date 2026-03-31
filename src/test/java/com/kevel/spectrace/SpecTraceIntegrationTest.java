package com.kevel.spectrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

class SpecTraceIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void tracedJUnitAndPropertyMethodsPassAgainstTheFullCatalog() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ReviewOnlyFixtures.class)));

        assertEquals(0, result.failedCount());
        assertEquals(4, result.succeededCount());
    }

    @Test
    void subsetRunsStillValidateAgainstTheFullCanonicalCatalog() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class)));

        assertEquals(0, result.failedCount());
        assertEquals(1, result.succeededCount());
    }

    @Test
    void identifiersDeclaredOnHeadersAreDiscoveredWithCleanHeadingContext() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        SpecCatalog catalog = SpecCatalogBuilder.build(projectRoot);

        assertEquals(
                "Requirement: Traced JUnit methods validate known identifiers",
                catalog.definition("TRACE-JUNIT").headingContext());
        assertEquals(
                "Scenario: Property coverage can span files",
                catalog.definition("TRACE-SECONDARY").headingContext());
        assertEquals(1, catalog.definition("TRACE-JUNIT").lineNumber());
        assertEquals(3, catalog.definition("TRACE-SECONDARY").lineNumber());
    }

    @Test
    void unknownIdentifierFailsOnlyTheDeclaringMethodAndOtherMethodsContinue() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));
        SpecTraceHarnessFixtures.ContinuationFixtures.reset();

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ContinuationFixtures.class)));

        assertEquals(1, result.failedCount());
        assertEquals(1, result.succeededCount());
        assertEquals(1, SpecTraceHarnessFixtures.ContinuationFixtures.executedMethods.get());
        assertTrue(
                result.firstFailureMessage().orElseThrow().contains("Unknown @SpecTrace identifier 'TRACE-UNKNOWN'"));
        assertTrue(
                result.firstFailureMessage()
                        .orElseThrow()
                        .contains(
                                "com.kevel.spectrace.SpecTraceHarnessFixtures$ContinuationFixtures#unknownIdentifierFailsBeforeExecution"));
    }

    @Test
    void malformedIdentifiersReportAFormatError() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectMethod(
                        SpecTraceHarnessFixtures.InvalidAnnotationFixtures.class, "malformedIdentifierFails")));

        assertEquals(1, result.failedCount());
        assertTrue(
                result.firstFailureMessage().orElseThrow().contains("Malformed @SpecTrace identifier 'trace-junit'"));
    }

    @Test
    void emptyAnnotationArraysFailAsConfigurationErrors() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectMethod(
                        SpecTraceHarnessFixtures.InvalidAnnotationFixtures.class, "emptyAnnotationFails")));

        assertEquals(1, result.failedCount());
        assertTrue(result.firstFailureMessage().orElseThrow().contains("Empty @SpecTrace declaration"));
    }

    @Test
    void unannotatedTestsAreUnaffectedByTheHarness() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.UnannotatedFixtures.class)));

        assertEquals(0, result.failedCount());
        assertEquals(1, result.succeededCount());
    }

    @Test
    void duplicateCatalogDefinitionsFailBeforeTracedMethodsRun() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/catalog/duplicate", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class)));

        assertEquals(1, result.failedCount());
        assertTrue(result.firstFailureMessage()
                .orElseThrow()
                .contains("Duplicate canonical spec identifiers were found across files"));
        assertTrue(result.firstFailureMessage().orElseThrow().contains("openspec/specs/alpha/spec.md:1"));
        assertTrue(result.firstFailureMessage().orElseThrow().contains("openspec/specs/beta/spec.md:1"));
    }

    @Test
    void coverageCanBeDisabledOrEnabledIndependently() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult coverageOff = run(
                projectRoot,
                false,
                List.of(
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ReviewOnlyFixtures.class)));
        RunResult coverageOn = run(
                projectRoot,
                true,
                List.of(
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ReviewOnlyFixtures.class)));

        assertEquals(0, coverageOff.failedCount());
        assertEquals(1, coverageOn.failedCount());
        assertTrue(coverageOn.firstFailureMessage().orElseThrow().contains("TRACE-UNUSED"));
        assertTrue(coverageOn.firstFailureMessage().orElseThrow().contains("openspec/specs/primary/spec.md:9"));
        assertTrue(
                coverageOn.firstFailureMessage().orElseThrow().contains("Requirement: Uncovered coverage is reported"));
    }

    @Test
    void coveragePassesWhenAllIdentifiersAreExercised() throws Exception {
        Path projectRoot = copyResourceDirectory("spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                true,
                List.of(
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ReviewOnlyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.CoverageFixtures.class)));

        assertEquals(0, result.failedCount());
    }

    @Test
    void emptyCatalogCoverageTriviallyPasses() throws Exception {
        Files.createDirectories(tempDir.resolve("empty-project"));

        RunResult result = run(
                tempDir.resolve("empty-project"),
                true,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.UnannotatedFixtures.class)));

        assertEquals(0, result.failedCount());
        assertEquals(1, result.succeededCount());
    }

    @Test
    void tracedTestsFailAgainstAnEmptyCatalog() throws Exception {
        Files.createDirectories(tempDir.resolve("empty-project"));

        RunResult result = run(
                tempDir.resolve("empty-project"),
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class)));

        assertEquals(1, result.failedCount());
        assertTrue(result.firstFailureMessage().orElseThrow().contains("Unknown @SpecTrace identifier 'TRACE-JUNIT'"));
    }

    private static RunResult run(
            Path projectRoot,
            boolean coverageEnabled,
            List<? extends org.junit.platform.engine.DiscoverySelector> selectors) {
        String previousRoot = System.getProperty(SpecTraceRuntime.ROOT_PROPERTY);
        String previousCoverage = System.getProperty(SpecTraceRuntime.COVERAGE_PROPERTY);
        String previousInterceptors = System.getProperty("junit.platform.launcher.interceptors.enabled");
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Throwable executionFailure = null;
        try {
            System.setProperty(SpecTraceRuntime.ROOT_PROPERTY, projectRoot.toString());
            System.setProperty(SpecTraceRuntime.COVERAGE_PROPERTY, Boolean.toString(coverageEnabled));
            System.setProperty("junit.platform.launcher.interceptors.enabled", "true");

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectors)
                    .build();
            try (LauncherSession session = LauncherFactory.openSession()) {
                try {
                    session.getLauncher().execute(request, listener);
                } catch (Throwable throwable) {
                    executionFailure = throwable;
                }
            }
        } finally {
            restoreProperty(SpecTraceRuntime.ROOT_PROPERTY, previousRoot);
            restoreProperty(SpecTraceRuntime.COVERAGE_PROPERTY, previousCoverage);
            restoreProperty("junit.platform.launcher.interceptors.enabled", previousInterceptors);
        }

        return new RunResult(listener, executionFailure);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static Path copyResourceDirectory(String resourceName, Path destination)
            throws IOException, URISyntaxException {
        Path source = Path.of(SpecTraceIntegrationTest.class
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

    private record RunResult(
            org.junit.platform.launcher.listeners.TestExecutionSummary summary, Throwable executionFailure) {

        RunResult(SummaryGeneratingListener listener) {
            this(listener.getSummary(), null);
        }

        RunResult(SummaryGeneratingListener listener, Throwable executionFailure) {
            this(listener.getSummary(), executionFailure);
        }

        long failedCount() {
            return summary.getTestsFailedCount() + (executionFailure == null ? 0 : 1);
        }

        long succeededCount() {
            return summary.getTestsSucceededCount();
        }

        Optional<String> firstFailureMessage() {
            Optional<String> summaryFailure = summary.getFailures().stream()
                    .map(org.junit.platform.launcher.listeners.TestExecutionSummary.Failure::getException)
                    .map(Throwable::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .findFirst();
            if (summaryFailure.isPresent()) {
                return summaryFailure;
            }
            if (executionFailure == null) {
                return Optional.empty();
            }
            String message = executionFailure.getMessage();
            if (message == null || message.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(message);
        }
    }
}
