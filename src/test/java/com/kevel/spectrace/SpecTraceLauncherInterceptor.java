package com.kevel.spectrace;

import java.util.Objects;
import org.junit.platform.launcher.LauncherInterceptor;

/**
 * JUnit Platform interceptor that resets trace state and enforces end-of-run coverage.
 *
 * <p>The interceptor brackets each launcher invocation with runtime reset calls so separate launcher
 * sessions never leak identifier state into one another.
 *
 * <p>Example:
 *
 * <pre>{@code
 * try (LauncherSession session = LauncherFactory.openSession()) {
 *     session.getLauncher().execute(request);
 * }
 * }</pre>
 */
public final class SpecTraceLauncherInterceptor implements LauncherInterceptor {

    /**
     * Resets runtime state, delegates to the launcher, and checks coverage at the end of the run.
     *
     * <p>Preconditions: {@code invocation} is non-null. Postconditions: exercised-identifier state
     * is cleared before and after the delegated invocation.
     *
     * @param invocation launcher invocation supplied by the platform; must be non-null
     * @return delegated launcher result, which may be {@code null} for the terminal execute phase
     * @throws NullPointerException if {@code invocation} is null
     */
    @Override
    public <T> T intercept(Invocation<T> invocation) {
        Objects.requireNonNull(invocation, "invocation");
        SpecTraceRuntime.resetExecution();
        try {
            T result = invocation.proceed();
            // The platform signals the terminal execute phase with a null result, so that is the
            // only point where end-of-run coverage is complete and safe to evaluate.
            if (result == null) {
                SpecTraceRuntime.assertCoverageSatisfied();
            }
            return result;
        } finally {
            SpecTraceRuntime.resetExecution();
        }
    }

    /**
     * Closes the interceptor.
     *
     * <p>No additional cleanup is required because runtime state is reset per intercepted launch.
     */
    @Override
    public void close() {}
}
