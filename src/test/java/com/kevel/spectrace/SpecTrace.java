package com.kevel.spectrace;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import net.jqwik.api.lifecycle.PropagationMode;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Declares which canonical spec identifiers a test method exercises.
 *
 * <p>The annotation is a shared front door for both JUnit Jupiter and jqwik. Annotated methods are
 * validated before execution so malformed or unknown identifiers fail only the declaring method.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Test
 * @SpecTrace({"TRACE-JUNIT", "TRACE-SECONDARY"})
 * void tracedTest() {
 *     // test body
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtendWith(SpecTraceJupiterExtension.class)
@AddLifecycleHook(value = SpecTracePropertyHook.class, propagateTo = PropagationMode.NO_DESCENDANTS)
public @interface SpecTrace {

    /**
     * Returns the canonical identifiers exercised by the annotated method.
     *
     * <p>Preconditions: identifiers use the canonical bracket-token syntax without brackets,
     * e.g. {@code TRACE-JUNIT}. Postconditions: returned array order is preserved for diagnostics
     * and coverage recording.
     *
     * @return non-null array of canonical spec identifiers
     */
    String[] value();
}
