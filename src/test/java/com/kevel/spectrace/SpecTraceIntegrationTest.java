package com.kevel.spectrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * End-to-end integration tests for the spec-traceability harness.
 *
 * <p>These tests execute embedded JUnit Platform sessions so they can validate extension wiring,
 * launcher interception, coverage enforcement, and failure granularity without depending on the
 * outer Maven test run.
 *
 * <p>Example:
 *
 * <pre>{@code
 * RunResult result = run(projectRoot, false, List.of(DiscoverySelectors.selectClass(...)));
 * assertEquals(0, result.failedCount());
 * }</pre>
 */
class SpecTraceIntegrationTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that traced JUnit and jqwik methods pass together against the full catalog.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void tracedJUnitAndPropertyMethodsPassAgainstTheFullCatalog() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

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

    /**
     * Verifies that a subset run still validates identifiers against the full canonical catalog.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void subsetRunsStillValidateAgainstTheFullCanonicalCatalog() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class)));

        assertEquals(0, result.failedCount());
        assertEquals(1, result.succeededCount());
    }

    /**
     * Verifies that header-based identifiers preserve clean heading context after sanitization.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void identifiersDeclaredOnHeadersAreDiscoveredWithCleanHeadingContext() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

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

    /**
     * Verifies that an unknown identifier fails only the declaring method and sibling methods still
     * run.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void unknownIdentifierFailsOnlyTheDeclaringMethodAndOtherMethodsContinue() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));
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

    /**
     * Verifies that malformed identifiers fail with a format-specific message.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void malformedIdentifiersReportAFormatError() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectMethod(
                        SpecTraceHarnessFixtures.InvalidAnnotationFixtures.class, "malformedIdentifierFails")));

        assertEquals(1, result.failedCount());
        assertTrue(
                result.firstFailureMessage().orElseThrow().contains("Malformed @SpecTrace identifier 'trace-junit'"));
    }

    /**
     * Verifies that empty annotation arrays are treated as configuration errors.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void emptyAnnotationArraysFailAsConfigurationErrors() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectMethod(
                        SpecTraceHarnessFixtures.InvalidAnnotationFixtures.class, "emptyAnnotationFails")));

        assertEquals(1, result.failedCount());
        assertTrue(result.firstFailureMessage().orElseThrow().contains("Empty @SpecTrace declaration"));
    }

    /**
     * Verifies that unannotated tests are ignored by the harness.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void unannotatedTestsAreUnaffectedByTheHarness() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = run(
                projectRoot,
                false,
                List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.UnannotatedFixtures.class)));

        assertEquals(0, result.failedCount());
        assertEquals(1, result.succeededCount());
    }

    /**
     * Verifies that duplicate catalog definitions fail before traced methods execute.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void duplicateCatalogDefinitionsFailBeforeTracedMethodsRun() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/catalog/duplicate", tempDir.resolve("project"));

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

    /**
     * Verifies that coverage enforcement is controlled independently from identifier validation.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void coverageCanBeDisabledOrEnabledIndependently() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

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

    /**
     * Verifies that coverage passes when all canonical identifiers are exercised in one run.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void coveragePassesWhenAllIdentifiersAreExercised() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

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

    /**
     * Verifies coverage accumulation across multiple execute invocations in one launcher session.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void coverageAccumulatesAcrossMultipleExecuteInvocationsInOneSession() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult result = runMultiExecute(
                projectRoot,
                true,
                List.of(
                        List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class)),
                        List.of(
                                DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class),
                                DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ReviewOnlyFixtures.class),
                                DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.CoverageFixtures.class))));

        assertEquals(0, result.failedCount());
    }

    /**
     * Verifies coverage failure in one session does not leak exercised ids into a later session.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
    @Test
    void coverageStateIsIsolatedAcrossSessions() throws Exception {
        Path projectRoot = SpecTraceTestResources.copyResourceDirectory(
                SpecTraceIntegrationTest.class, "spec-traceability/integration/project", tempDir.resolve("project"));

        RunResult first = runMultiExecute(
                projectRoot,
                true,
                List.of(List.of(DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.JupiterFixtures.class))));
        assertEquals(1, first.failedCount());
        assertTrue(first.firstFailureMessage().orElseThrow().contains("TRACE-PROPERTY"));

        RunResult second = runMultiExecute(
                projectRoot,
                true,
                List.of(List.of(
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.PropertyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.ReviewOnlyFixtures.class),
                        DiscoverySelectors.selectClass(SpecTraceHarnessFixtures.CoverageFixtures.class))));
        assertEquals(1, second.failedCount());
        assertTrue(second.firstFailureMessage().orElseThrow().contains("TRACE-JUNIT"));
    }

    /**
     * Verifies that an empty catalog satisfies coverage vacuously.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
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

    /**
     * Verifies that traced tests fail against an empty catalog because identifiers are unknown.
     *
     * @throws Exception if fixture setup fails unexpectedly
     */
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

    /**
     * Runs an isolated embedded launcher session against the given project root.
     *
     * <p>Safety requirement: this helper restores every mutated system property before returning so
     * later tests observe a clean environment.
     *
     * @param projectRoot temporary project root for the run; must be non-null
     * @param coverageEnabled whether end-of-run coverage enforcement is enabled
     * @param selectors non-null selectors for the embedded launcher request
     * @return summary of test outcomes and any launcher-level failure
     */
    private static RunResult run(
            Path projectRoot, boolean coverageEnabled, List<? extends DiscoverySelector> selectors) {
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
            try {
                try (LauncherSession session = LauncherFactory.openSession()) {
                    session.getLauncher().execute(request, listener);
                }
            } catch (Throwable throwable) {
                executionFailure = throwable;
            }
        } finally {
            restoreProperty(SpecTraceRuntime.ROOT_PROPERTY, previousRoot);
            restoreProperty(SpecTraceRuntime.COVERAGE_PROPERTY, previousCoverage);
            restoreProperty("junit.platform.launcher.interceptors.enabled", previousInterceptors);
        }

        return new RunResult(listener, executionFailure);
    }

    /**
     * Runs multiple execute invocations inside one launcher session.
     *
     * @param projectRoot temporary project root for the run; must be non-null
     * @param coverageEnabled whether end-of-run coverage enforcement is enabled
     * @param selectorBatches non-null per-execute selector groups
     * @return summary of test outcomes and any launcher-level failure
     */
    private static RunResult runMultiExecute(
            Path projectRoot, boolean coverageEnabled, List<List<? extends DiscoverySelector>> selectorBatches) {
        String previousRoot = System.getProperty(SpecTraceRuntime.ROOT_PROPERTY);
        String previousCoverage = System.getProperty(SpecTraceRuntime.COVERAGE_PROPERTY);
        String previousInterceptors = System.getProperty("junit.platform.launcher.interceptors.enabled");
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Throwable executionFailure = null;
        try {
            System.setProperty(SpecTraceRuntime.ROOT_PROPERTY, projectRoot.toString());
            System.setProperty(SpecTraceRuntime.COVERAGE_PROPERTY, Boolean.toString(coverageEnabled));
            System.setProperty("junit.platform.launcher.interceptors.enabled", "true");

            try {
                try (LauncherSession session = LauncherFactory.openSession()) {
                    for (List<? extends DiscoverySelector> selectors : selectorBatches) {
                        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                                .selectors(selectors)
                                .build();
                        session.getLauncher().execute(request, listener);
                    }
                }
            } catch (Throwable throwable) {
                executionFailure = throwable;
            }
        } finally {
            restoreProperty(SpecTraceRuntime.ROOT_PROPERTY, previousRoot);
            restoreProperty(SpecTraceRuntime.COVERAGE_PROPERTY, previousCoverage);
            restoreProperty("junit.platform.launcher.interceptors.enabled", previousInterceptors);
        }

        return new RunResult(listener, executionFailure);
    }

    /**
     * Restores a system property to its previous value after an embedded launcher run.
     *
     * @param name property name; must be non-null
     * @param value previous value, or {@code null} when the property should be cleared
     */
    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, value);
    }

    /**
     * Small immutable summary of an embedded launcher run.
     *
     * <p>Example:
     *
     * <pre>{@code
     * long failures = result.failedCount();
     * Optional<String> message = result.firstFailureMessage();
     * }</pre>
     *
     * @param summary JUnit Platform summary; must be non-null
     * @param executionFailure launcher-level failure thrown outside per-test reporting, or
     *     {@code null}
     */
    private record RunResult(TestExecutionSummary summary, Throwable executionFailure) {

        /**
         * Convenience constructor for listener-backed runs with optional launcher failure.
         *
         * @param listener listener holding the final summary; must be non-null
         * @param executionFailure launcher-level failure outside per-test reporting, or {@code null}
         */
        RunResult(SummaryGeneratingListener listener, Throwable executionFailure) {
            this(listener.getSummary(), executionFailure);
        }

        /**
         * Returns the total failed count, including launcher-level failures.
         *
         * @return failed test count plus one when a launcher-level failure occurred
         */
        long failedCount() {
            return summary.getTestsFailedCount() + (executionFailure == null ? 0 : 1);
        }

        /**
         * Returns the count of succeeded tests reported by the platform.
         *
         * @return succeeded test count
         */
        long succeededCount() {
            return summary.getTestsSucceededCount();
        }

        /**
         * Returns the first non-blank failure message from either the summary or launcher failure.
         *
         * @return optional human-readable failure message
         */
        Optional<String> firstFailureMessage() {
            Optional<String> summaryFailure = summary.getFailures().stream()
                    .map(TestExecutionSummary.Failure::getException)
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
