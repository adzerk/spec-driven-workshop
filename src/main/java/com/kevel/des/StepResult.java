package com.kevel.des;

import java.util.Objects;

/**
 * Result of executing one simulation step.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code previousTime >= 0}
 *   <li>{@code newTime >= previousTime}
 *   <li>{@code executedEventId != null}
 *   <li>{@code previousState != null}
 *   <li>{@code newState != null}
 * </ul>
 */
public record StepResult<S>(long previousTime, long newTime, EventId executedEventId, S previousState, S newState) {

    public StepResult {
        if (previousTime < 0) {
            throw new IllegalArgumentException("previousTime must be >= 0");
        }
        if (newTime < previousTime) {
            throw new IllegalArgumentException("newTime must be >= previousTime");
        }
        Objects.requireNonNull(executedEventId, "executedEventId must not be null");
        Objects.requireNonNull(previousState, "previousState must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
    }
}
