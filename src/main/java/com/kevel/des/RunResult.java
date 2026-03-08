package com.kevel.des;

import java.util.List;
import java.util.Objects;

/**
 * Result of a run operation, including the complete step trace.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code stepsExecuted >= 0}
 *   <li>{@code startTime >= 0}
 *   <li>{@code endTime >= startTime}
 *   <li>{@code finalState != null}
 *   <li>{@code stepResults != null}
 *   <li>{@code stepResults.size() == stepsExecuted}
 * </ul>
 */
public record RunResult<S>(
        long stepsExecuted,
        long startTime,
        long endTime,
        S finalState,
        boolean stoppedByCondition,
        List<StepResult<S>> stepResults) {

    public RunResult {
        if (stepsExecuted < 0) {
            throw new IllegalArgumentException("stepsExecuted must be >= 0");
        }
        if (startTime < 0) {
            throw new IllegalArgumentException("startTime must be >= 0");
        }
        if (endTime < startTime) {
            throw new IllegalArgumentException("endTime must be >= startTime");
        }
        Objects.requireNonNull(finalState, "finalState must not be null");
        Objects.requireNonNull(stepResults, "stepResults must not be null");

        List<StepResult<S>> copy = List.copyOf(stepResults);
        if (copy.size() != stepsExecuted) {
            throw new IllegalArgumentException("stepResults size must equal stepsExecuted");
        }

        stepResults = copy;
    }
}
