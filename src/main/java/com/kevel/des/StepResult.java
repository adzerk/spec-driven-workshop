package com.kevel.des;

import java.util.Objects;

/**
 * Result of executing one simulation step.
 *
 * <p>Postconditions captured by this value: exactly one event was executed, simulation time moved
 * from {@code previousTime} to {@code newTime} with {@code newTime >= previousTime}, and state
 * moved from {@code previousState} to {@code newState}.
 *
 * @param previousTime simulation time before the step
 * @param newTime simulation time after the step
 * @param eventId identifier of the executed event
 * @param previousState state before executing the event action
 * @param newState state after executing the event action
 */
public record StepResult<S>(long previousTime, long newTime, EventId eventId, S previousState, S newState) {

    public StepResult {
        if (newTime < previousTime) {
            throw new IllegalArgumentException("newTime must be >= previousTime");
        }
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(previousState, "previousState must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
    }
}
