package com.kevel.spectrace;

/**
 * Legacy no-op launcher interceptor retained for backward compatibility.
 *
 * <p>Lifecycle ownership moved to {@link SpecTraceSessionListener}, which scopes reset and coverage
 * assertion to launcher-session open/close boundaries.
 */
@Deprecated
public final class SpecTraceLauncherInterceptor {}
