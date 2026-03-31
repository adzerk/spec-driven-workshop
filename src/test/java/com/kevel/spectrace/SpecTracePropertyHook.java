package com.kevel.spectrace;

import net.jqwik.api.lifecycle.AroundPropertyHook;
import net.jqwik.api.lifecycle.PropertyExecutionResult;
import net.jqwik.api.lifecycle.PropertyExecutor;
import net.jqwik.api.lifecycle.PropertyLifecycleContext;

public final class SpecTracePropertyHook implements AroundPropertyHook {

    @Override
    public PropertyExecutionResult aroundProperty(PropertyLifecycleContext context, PropertyExecutor property)
            throws Throwable {
        SpecTraceRuntime.validateAndRecord(context.targetMethod());
        return property.execute();
    }
}
