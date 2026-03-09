package com.kevel.des;

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
public record RunResult<S>(
        long stepsExecuted, long startTime, long endTime, S finalState, boolean stoppedByCondition) {}
