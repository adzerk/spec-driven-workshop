package com.kevel.spectrace;

import java.util.Objects;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit Jupiter callback that validates {@link SpecTrace} identifiers before each test method.
 *
 * <p>The extension is intentionally small so the runtime owns validation rules and shared state.
 * This keeps the JUnit-specific boundary thin and deterministic.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Test
 * @SpecTrace({"TRACE-JUNIT"})
 * void tracedMethod() {
 *     // validation runs before this body executes
 * }
 * }</pre>
 */
public final class SpecTraceJupiterExtension implements BeforeEachCallback {

    /**
     * Validates and records the traced identifiers for the current JUnit test method.
     *
     * <p>Preconditions: {@code context} is non-null and refers to a concrete test method.
     * Postconditions: every declared identifier is validated against the canonical catalog before the
     * test body runs.
     *
     * @param context JUnit extension context for the current method; must be non-null
     * @throws NullPointerException if {@code context} is null
     * @throws AssertionError if the method declares malformed or unknown identifiers
     */
    @Override
    public void beforeEach(ExtensionContext context) {
        Objects.requireNonNull(context, "context");
        SpecTraceRuntime.validateAndRecord(context.getRequiredTestMethod());
    }
}
