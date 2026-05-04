package com.kevel.spectrace;

import java.util.Objects;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Session-scoped listener that manages spec-trace lifecycle boundaries.
 *
 * <p>Coverage accounting is reset exactly once at session start, accumulates across all launcher
 * invocations for that session, and is asserted once at session close.
 */
public final class SpecTraceSessionListener implements LauncherSessionListener {

    /**
     * Opens a spec-trace session boundary.
     *
     * @param session active launcher session; must be non-null
     */
    @Override
    public void launcherSessionOpened(LauncherSession session) {
        Objects.requireNonNull(session, "session");
        SpecTraceRuntime.sessionOpened();
    }

    /**
     * Closes a spec-trace session boundary and performs final coverage checks.
     *
     * @param session active launcher session; must be non-null
     */
    @Override
    public void launcherSessionClosed(LauncherSession session) {
        Objects.requireNonNull(session, "session");
        SpecTraceRuntime.sessionClosed();
    }
}
