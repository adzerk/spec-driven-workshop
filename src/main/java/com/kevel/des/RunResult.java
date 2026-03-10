package com.kevel.des;

import java.util.Objects;

/**
 * Result of a simulation run operation.
 *
 * <p>Postconditions captured by this value: {@code stepsExecuted >= 0}, {@code endTime >=
 * startTime}, and {@code finalState} is the simulation state at the time the run stopped.
 *
 * @param stepsExecuted number of steps executed in the run
 * @param startTime simulation time at run start
 * @param endTime simulation time at run end
 * @param finalState final simulation state at run end
 * @param stoppedByCondition true when stopped by a boundary condition rather than empty queue
 */
public record RunResult<S>(long stepsExecuted, long startTime, long endTime, S finalState, boolean stoppedByCondition) {

    public RunResult {
        if (stepsExecuted < 0) {
            throw new IllegalArgumentException("stepsExecuted must be non-negative");
        }
        if (endTime < startTime) {
            throw new IllegalArgumentException("endTime must be >= startTime");
        }
        Objects.requireNonNull(finalState, "finalState must not be null");
    }
}
