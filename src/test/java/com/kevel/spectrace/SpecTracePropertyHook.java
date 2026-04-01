package com.kevel.spectrace;

import java.util.Objects;
import net.jqwik.api.lifecycle.AroundPropertyHook;
import net.jqwik.api.lifecycle.PropertyExecutionResult;
import net.jqwik.api.lifecycle.PropertyExecutor;
import net.jqwik.api.lifecycle.PropertyLifecycleContext;

/**
 * jqwik lifecycle hook that applies the shared {@link SpecTrace} validation rules to properties.
 *
 * <p>The hook mirrors the Jupiter extension so property-based tests participate in the same
 * catalog validation and coverage accounting.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Property
 * @SpecTrace({"TRACE-PROPERTY"})
 * void tracedProperty(@ForAll int value) {
 *     // validation runs before jqwik executes the property
 * }
 * }</pre>
 */
public final class SpecTracePropertyHook implements AroundPropertyHook {

    /**
     * Validates traced identifiers and then delegates to jqwik's property executor.
     *
     * @param context jqwik lifecycle context for the property; must be non-null
     * @param property jqwik executor for the property; must be non-null
     * @return jqwik execution result from the delegated property run
     * @throws NullPointerException if {@code context} or {@code property} is null
     * @throws Throwable if jqwik execution fails after validation succeeds
     */
    @Override
    public PropertyExecutionResult aroundProperty(PropertyLifecycleContext context, PropertyExecutor property)
            throws Throwable {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(property, "property");
        SpecTraceRuntime.validateAndRecord(context.targetMethod());
        return property.execute();
    }
}
