package com.kevel.des;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationTest {

    @Test
    void create_withNullInitialState_throws() {
        assertThrows(NullPointerException.class, () -> Simulation.create(null));
    }

    @Test
    void create_initialObserversMatchContract() {
        Simulation<Integer> simulation = Simulation.create(7);

        assertEquals(0L, simulation.now());
        assertEquals(7, simulation.state());
        assertTrue(simulation.isEmpty());
        assertTrue(simulation.peekNext().isEmpty());
    }

    @Test
    void scheduleAt_rejectsPastTime() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(5, value -> value + 1);
        simulation.step();

        assertThrows(IllegalArgumentException.class, () -> simulation.scheduleAt(4, value -> value + 1));
    }

    @Test
    void scheduleIn_rejectsNegativeDelay() {
        Simulation<Integer> simulation = Simulation.create(0);
        assertThrows(IllegalArgumentException.class, () -> simulation.scheduleIn(-1, value -> value + 1));
    }

    @Test
    void scheduleAt_andScheduleIn_rejectNullAction() {
        Simulation<Integer> simulation = Simulation.create(0);

        assertThrows(NullPointerException.class, () -> simulation.scheduleAt(0, null));
        assertThrows(NullPointerException.class, () -> simulation.scheduleIn(0, null));
    }

    @Test
    void scheduleIn_overflowThrowsArithmeticException() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(Long.MAX_VALUE, value -> value);
        simulation.step();

        assertThrows(ArithmeticException.class, () -> simulation.scheduleIn(1, value -> value));
    }

    @Test
    void lowerTimeExecutesBeforeHigherTime() {
        Simulation<String> simulation = Simulation.create("");
        simulation.scheduleAt(10, state -> state + "B");
        simulation.scheduleAt(5, state -> state + "A");

        StepResult<String> first = simulation.step();
        StepResult<String> second = simulation.step();

        assertEquals(5L, first.newTime());
        assertEquals("A", first.newState());
        assertEquals(10L, second.newTime());
        assertEquals("AB", second.newState());
    }

    @Test
    void sameTimeExecutesFifoByScheduleOrder() {
        Simulation<String> simulation = Simulation.create("");
        simulation.scheduleAt(3, state -> state + "A");
        simulation.scheduleAt(3, state -> state + "B");
        simulation.scheduleAt(3, state -> state + "C");

        assertEquals("A", simulation.step().newState());
        assertEquals("AB", simulation.step().newState());
        assertEquals("ABC", simulation.step().newState());
    }

    @Test
    void peekNext_isNonMutating() {
        Simulation<Integer> simulation = Simulation.create(11);
        simulation.scheduleAt(4, value -> value + 1);

        Event<Integer> firstPeek = simulation.peekNext().orElseThrow();
        Event<Integer> secondPeek = simulation.peekNext().orElseThrow();

        assertEquals(firstPeek, secondPeek);
        assertEquals(0L, simulation.now());
        assertEquals(11, simulation.state());
    }

    @Test
    void step_onEmptyQueue_throws() {
        Simulation<Integer> simulation = Simulation.create(0);
        assertThrows(IllegalStateException.class, simulation::step);
    }

    @Test
    void step_updatesTimeAndStateAccordingToAction() {
        Simulation<Integer> simulation = Simulation.create(10);
        EventId eventId = simulation.scheduleAt(8, value -> value * 2);

        StepResult<Integer> result = simulation.step();

        assertEquals(0L, result.previousTime());
        assertEquals(8L, result.newTime());
        assertEquals(eventId, result.executedEventId());
        assertEquals(10, result.previousState());
        assertEquals(20, result.newState());
        assertEquals(8L, simulation.now());
        assertEquals(20, simulation.state());
    }

    @Test
    void run_drainsQueueAndProducesConsistentTrace() {
        Simulation<Integer> simulation = Simulation.create(1);
        simulation.scheduleAt(2, value -> value + 5);
        simulation.scheduleAt(4, value -> value * 2);
        simulation.scheduleAt(4, value -> value - 3);

        RunResult<Integer> result = simulation.run();

        assertTrue(simulation.isEmpty());
        assertFalse(result.stoppedByCondition());
        assertEquals(3L, result.stepsExecuted());
        assertEquals(3, result.stepResults().size());
        assertEquals(result.finalState(), simulation.state());
        assertEquals(result.endTime(), simulation.now());

        assertTrue(isMonotone(result.stepResults()));
        StepResult<Integer> last = result.stepResults().getLast();
        assertEquals(last.newState(), result.finalState());
        assertEquals(last.newTime(), result.endTime());

        assertThrows(
                UnsupportedOperationException.class, () -> result.stepResults().add(null));
    }

    @Test
    void runUntilTime_executesOnlyBoundedEvents() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(2, value -> value + 1);
        simulation.scheduleAt(5, value -> value + 10);
        simulation.scheduleAt(7, value -> value + 100);

        RunResult<Integer> result = simulation.runUntilTime(5);

        assertEquals(2L, result.stepsExecuted());
        assertEquals(11, result.finalState());
        assertTrue(simulation.peekNext().isPresent());
        assertEquals(7L, simulation.peekNext().orElseThrow().time());
    }

    @Test
    void runUntilTime_rejectsEndBeforeNow() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(4, value -> value + 1);
        simulation.step();

        assertThrows(IllegalArgumentException.class, () -> simulation.runUntilTime(3));
    }

    @Test
    void runUntil_conditionControlsStoppingFlag() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(1, value -> value + 1);
        simulation.scheduleAt(2, value -> value + 1);
        simulation.scheduleAt(3, value -> value + 1);

        RunResult<Integer> stopped = simulation.runUntil(sim -> sim.state() >= 2);
        assertTrue(stopped.stoppedByCondition());
        assertEquals(2, stopped.finalState());
        assertFalse(simulation.isEmpty());

        RunResult<Integer> drained = simulation.runUntil(sim -> false);
        assertFalse(drained.stoppedByCondition());
        assertTrue(simulation.isEmpty());
    }

    @Test
    void runUntil_rejectsNullCondition() {
        Simulation<Integer> simulation = Simulation.create(0);
        assertThrows(NullPointerException.class, () -> simulation.runUntil(null));
    }

    @Test
    void deterministicScheduleProducesIdenticalTrace() {
        Simulation<Integer> left = Simulation.create(5);
        Simulation<Integer> right = Simulation.create(5);

        scheduleDeterministicProgram(left);
        scheduleDeterministicProgram(right);

        RunResult<Integer> leftRun = left.run();
        RunResult<Integer> rightRun = right.run();

        assertEquals(leftRun.finalState(), rightRun.finalState());
        assertEquals(leftRun.stepResults().size(), rightRun.stepResults().size());

        for (int i = 0; i < leftRun.stepResults().size(); i++) {
            StepResult<Integer> leftStep = leftRun.stepResults().get(i);
            StepResult<Integer> rightStep = rightRun.stepResults().get(i);
            assertEquals(leftStep.newTime(), rightStep.newTime());
            assertEquals(leftStep.newState(), rightStep.newState());
        }
    }

    private static void scheduleDeterministicProgram(Simulation<Integer> simulation) {
        simulation.scheduleAt(4, value -> value + 1);
        simulation.scheduleAt(1, value -> value * 2);
        simulation.scheduleIn(1, value -> value - 3);
        simulation.scheduleAt(4, value -> value + 8);
    }

    private static boolean isMonotone(List<StepResult<Integer>> steps) {
        if (steps.isEmpty()) {
            return true;
        }

        long previous = steps.getFirst().newTime();
        for (int i = 1; i < steps.size(); i++) {
            long current = steps.get(i).newTime();
            if (current < previous) {
                return false;
            }
            previous = current;
        }
        return true;
    }
}
