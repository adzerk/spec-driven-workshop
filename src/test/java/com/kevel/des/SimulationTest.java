package com.kevel.des;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SimulationTest {

    @Test
    void create_initializesDeterministicState() {
        Simulation<String> simulation = Simulation.create("init");

        assertEquals(0L, simulation.currentTime());
        assertEquals("init", simulation.currentState());
        assertTrue(simulation.isQueueEmpty());
        assertTrue(simulation.peekNext().isEmpty());
    }

    @Test
    void create_rejectsNullInitialState() {
        assertThrows(NullPointerException.class, () -> Simulation.create(null));
    }

    @Test
    void scheduleValidation_rejectsNullPastNegativeAndOverflow() {
        Simulation<Integer> simulation = Simulation.create(0);

        assertThrows(NullPointerException.class, () -> simulation.scheduleAt(0, null));
        assertThrows(NullPointerException.class, () -> simulation.scheduleIn(0, null));
        assertThrows(IllegalArgumentException.class, () -> simulation.scheduleIn(-1, s -> ActionResult.of(s)));

        simulation.scheduleAt(5, s -> ActionResult.of(s));
        simulation.step();
        assertThrows(IllegalArgumentException.class, () -> simulation.scheduleAt(4, s -> ActionResult.of(s)));

        simulation.scheduleAt(Long.MAX_VALUE, s -> ActionResult.of(s));
        simulation.step();
        assertThrows(ArithmeticException.class, () -> simulation.scheduleIn(1, s -> ActionResult.of(s)));
    }

    @Test
    void scheduleAtCurrentTime_preservesFifoOrderingForEqualTime() {
        Simulation<String> simulation = Simulation.create("");

        simulation.scheduleAt(0, s -> ActionResult.of(s + "A"));
        simulation.scheduleAt(0, s -> ActionResult.of(s + "B"));
        simulation.scheduleAt(0, s -> ActionResult.of(s + "C"));

        simulation.run();
        assertEquals("ABC", simulation.currentState());
    }

    @Test
    void scheduling_returnsUniqueEventIdsWithinSimulation() {
        Simulation<Integer> simulation = Simulation.create(0);
        EventId first = simulation.scheduleAt(1, s -> ActionResult.of(s));
        EventId second = simulation.scheduleIn(2, s -> ActionResult.of(s));

        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.equals(second));
    }

    @Test
    void step_executesOneEventAndReturnsBeforeAfterDetails() {
        Simulation<Integer> simulation = Simulation.create(10);
        EventId eventId = simulation.scheduleAt(7, s -> ActionResult.of(s + 5));

        StepResult<Integer> result = simulation.step();

        assertEquals(0L, result.previousTime());
        assertEquals(7L, result.newTime());
        assertEquals(10, result.previousState());
        assertEquals(15, result.newState());
        assertEquals(eventId, result.eventId());
        assertEquals(7L, simulation.currentTime());
        assertEquals(15, simulation.currentState());
    }

    @Test
    void step_rejectsEmptyQueue() {
        Simulation<Integer> simulation = Simulation.create(0);
        assertThrows(IllegalStateException.class, simulation::step);
    }

    @Test
    void actionResultSelfScheduling_enqueuesProducedEvents() {
        Simulation<String> simulation = Simulation.create("start");
        simulation.scheduleAt(
                5,
                s -> new ActionResult<>(
                        s + "-first",
                        List.of(new ActionResult.ScheduledEvent<>(8, inner -> ActionResult.of(inner + "-second")))));

        simulation.step();
        assertTrue(simulation.peekNext().isPresent());
        assertEquals(8L, simulation.peekNext().orElseThrow().time());

        simulation.step();
        assertEquals("start-first-second", simulation.currentState());
    }

    @Test
    void actionResultSelfScheduling_rejectsProducedEventInPast() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(
                5,
                s -> new ActionResult<>(
                        s + 1, List.of(new ActionResult.ScheduledEvent<>(4, inner -> ActionResult.of(inner)))));

        assertThrows(IllegalArgumentException.class, simulation::step);
    }

    @Test
    void actionProducedEvents_integrateIntoGlobalOrdering() {
        Simulation<String> simulation = Simulation.create("");

        simulation.scheduleAt(
                5,
                s -> new ActionResult<>(
                        s + "A",
                        List.of(new ActionResult.ScheduledEvent<>(10, inner -> ActionResult.of(inner + "C")))));
        simulation.scheduleAt(10, s -> ActionResult.of(s + "B"));

        simulation.run();
        assertEquals("ABC", simulation.currentState());
    }

    @Test
    void runVariants_coverDrainTimeBoundAndPredicateBoundaries() {
        Simulation<Integer> drain = Simulation.create(0);
        drain.scheduleAt(1, s -> ActionResult.of(s + 1));
        drain.scheduleAt(2, s -> ActionResult.of(s + 1));
        RunResult<Integer> drained = drain.run();
        assertEquals(2L, drained.stepsExecuted());
        assertTrue(drain.isQueueEmpty());

        Simulation<Integer> empty = Simulation.create(42);
        RunResult<Integer> emptyRun = empty.run();
        assertEquals(0L, emptyRun.stepsExecuted());
        assertEquals(42, emptyRun.finalState());

        Simulation<Integer> bounded = Simulation.create(0);
        bounded.scheduleAt(2, s -> ActionResult.of(s + 1));
        bounded.scheduleAt(5, s -> ActionResult.of(s + 1));
        bounded.scheduleAt(8, s -> ActionResult.of(s + 1));
        RunResult<Integer> boundResult = bounded.runUntilTime(5);
        assertEquals(2L, boundResult.stepsExecuted());
        assertEquals(2, bounded.currentState());
        assertEquals(8L, bounded.peekNext().orElseThrow().time());
        assertThrows(IllegalArgumentException.class, () -> bounded.runUntilTime(4));

        Simulation<Integer> predicate = Simulation.create(0);
        predicate.scheduleAt(1, s -> ActionResult.of(s + 1));
        predicate.scheduleAt(2, s -> ActionResult.of(s + 1));
        RunResult<Integer> predicateResult = predicate.runUntil(s -> s >= 1);
        assertEquals(1L, predicateResult.stepsExecuted());
        assertEquals(1, predicate.currentState());

        Simulation<Integer> immediate = Simulation.create(10);
        immediate.scheduleAt(100, s -> ActionResult.of(s + 100));
        RunResult<Integer> immediateResult = immediate.runUntil(s -> s >= 10);
        assertEquals(0L, immediateResult.stepsExecuted());
        assertEquals(10, immediate.currentState());
        assertThrows(NullPointerException.class, () -> immediate.runUntil(null));
    }

    @Test
    void peekNext_isNonMutatingAndStableAcrossRepeatedCalls() {
        Simulation<Integer> simulation = Simulation.create(0);
        simulation.scheduleAt(3, s -> ActionResult.of(s + 1));

        Event<Integer> firstPeek = simulation.peekNext().orElseThrow();
        long timeBefore = simulation.currentTime();
        Integer stateBefore = simulation.currentState();
        Event<Integer> secondPeek = simulation.peekNext().orElseThrow();

        assertSame(firstPeek, secondPeek);
        assertEquals(timeBefore, simulation.currentTime());
        assertEquals(stateBefore, simulation.currentState());

        simulation.step();
        assertTrue(simulation.peekNext().isEmpty());
    }

    @Test
    void determinism_holdsForEquivalentSchedulesIncludingSelfScheduling() {
        Simulation<String> first = Simulation.create("");
        Simulation<String> second = Simulation.create("");

        scheduleScript(first);
        scheduleScript(second);

        List<String> traceOne = trace(first);
        List<String> traceTwo = trace(second);

        assertEquals(traceOne, traceTwo);
        assertEquals(first.currentState(), second.currentState());
        assertEquals(first.currentTime(), second.currentTime());
    }

    private static void scheduleScript(Simulation<String> simulation) {
        Function<String, ActionResult<String>> actionA = s -> new ActionResult<>(
                s + "A", List.of(new ActionResult.ScheduledEvent<>(5, inner -> ActionResult.of(inner + "C"))));
        Function<String, ActionResult<String>> actionB = s -> ActionResult.of(s + "B");

        simulation.scheduleAt(3, actionA);
        simulation.scheduleAt(5, actionB);
    }

    private static List<String> trace(Simulation<String> simulation) {
        List<String> trace = new ArrayList<>();
        while (!simulation.isQueueEmpty()) {
            StepResult<String> step = simulation.step();
            trace.add(step.eventId().value() + "@" + step.newTime() + ":" + step.newState());
        }
        return trace;
    }
}
