package com.kevel.spectrace;

import org.junit.platform.launcher.LauncherInterceptor;

public final class SpecTraceLauncherInterceptor implements LauncherInterceptor {

    @Override
    public <T> T intercept(Invocation<T> invocation) {
        SpecTraceRuntime.resetExecution();
        try {
            T result = invocation.proceed();
            if (result == null) {
                SpecTraceRuntime.assertCoverageSatisfied();
            }
            return result;
        } finally {
            SpecTraceRuntime.resetExecution();
        }
    }

    @Override
    public void close() {}
}
