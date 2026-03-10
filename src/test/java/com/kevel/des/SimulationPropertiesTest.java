package com.kevel.des;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SimulationPropertiesTest {

    @Property
    void monotonicTime_fifoAndDeterministicReplay(@ForAll("schedules") List<ScheduleEntry> schedule) {
        Simulation<Integer> first = runSchedule(schedule);
        Simulation<Integer> second = runSchedule(schedule);

        List<ExecutionRecord> firstTrace = executionTrace(first);
        List<ExecutionRecord> secondTrace = executionTrace(second);

        List<Long> times = firstTrace.stream().map(ExecutionRecord::time).collect(Collectors.toList());
        assertTrue(isNonDecreasing(times));
        assertEquals(firstTrace, secondTrace);
    }

    @Property
    void modelBasedStateful_equivalentToReferenceEngine(@ForAll("schedules") List<ScheduleEntry> schedule) {
        Simulation<Integer> real = runSchedule(schedule);
        ReferenceResult reference = runReference(schedule);

        List<ExecutionRecord> realTrace = executionTrace(real);

        assertEquals(reference.trace(), realTrace);
        assertEquals(reference.finalTime(), real.currentTime());
        assertEquals(reference.finalState(), real.currentState());
    }

    @Provide
    Arbitrary<List<ScheduleEntry>> schedules() {
        Arbitrary<Integer> time = Arbitraries.integers().between(0, 20);
        Arbitrary<Integer> delta = Arbitraries.integers().between(0, 5);
        Arbitrary<Integer> producedCount = Arbitraries.integers().between(0, 2);
        Arbitrary<Boolean> useScheduleIn = Arbitraries.of(true, false);

        return Combinators.combine(time, delta, producedCount, useScheduleIn)
                .as((t, d, p, u) -> new ScheduleEntry(t, d, p, u))
                .list()
                .ofMinSize(0)
                .ofMaxSize(25);
    }

    private static Simulation<Integer> runSchedule(List<ScheduleEntry> schedule) {
        Simulation<Integer> simulation = Simulation.create(0);
        schedule.stream().sorted(Comparator.comparingInt(ScheduleEntry::time)).forEach(entry -> {
            if (entry.useScheduleIn()) {
                simulation.scheduleIn(entry.time(), state -> eventAction(state, entry));
            } else {
                simulation.scheduleAt(entry.time(), state -> eventAction(state, entry));
            }
        });
        return simulation;
    }

    private static ActionResult<Integer> eventAction(Integer state, ScheduleEntry entry) {
        int newState = state + 1;
        List<ActionResult.ScheduledEvent<Integer>> produced = new ArrayList<>();
        for (int i = 0; i < entry.producedCount(); i++) {
            long producedTime = (long) entry.time() + entry.delta() + i;
            produced.add(new ActionResult.ScheduledEvent<>(producedTime, s -> ActionResult.of(s + 1)));
        }
        return new ActionResult<>(newState, produced);
    }

    private static List<ExecutionRecord> executionTrace(Simulation<Integer> simulation) {
        List<ExecutionRecord> trace = new ArrayList<>();
        while (!simulation.isQueueEmpty()) {
            StepResult<Integer> step = simulation.step();
            trace.add(new ExecutionRecord(step.newTime(), step.eventId().value(), step.newState()));
        }
        return trace;
    }

    private static boolean isNonDecreasing(List<Long> values) {
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) < values.get(i - 1)) {
                return false;
            }
        }
        return true;
    }

    private static ReferenceResult runReference(List<ScheduleEntry> schedule) {
        PriorityQueue<ReferenceEvent> queue = new PriorityQueue<>();
        long sequence = 0L;
        long eventId = 0L;

        for (ScheduleEntry entry : schedule.stream()
                .sorted(Comparator.comparingInt(ScheduleEntry::time))
                .toList()) {
            queue.add(new ReferenceEvent(entry.time(), sequence++, eventId++, entry));
        }

        int state = 0;
        long time = 0;
        List<ExecutionRecord> trace = new ArrayList<>();

        while (!queue.isEmpty()) {
            ReferenceEvent event = queue.poll();
            time = event.time();
            state = state + 1;
            trace.add(new ExecutionRecord(time, event.eventId(), state));

            for (int i = 0; i < event.entry().producedCount(); i++) {
                long producedTime = (long) event.entry().time() + event.entry().delta() + i;
                queue.add(new ReferenceEvent(producedTime, sequence++, eventId++, new ScheduleEntry(0, 0, 0, false)));
            }
        }

        return new ReferenceResult(trace, time, state);
    }

    private record ScheduleEntry(int time, int delta, int producedCount, boolean useScheduleIn) {}

    private record ExecutionRecord(long time, long eventId, int newState) {}

    private record ReferenceResult(List<ExecutionRecord> trace, long finalTime, int finalState) {}

    private record ReferenceEvent(long time, long sequence, long eventId, ScheduleEntry entry)
            implements Comparable<ReferenceEvent> {
        @Override
        public int compareTo(ReferenceEvent other) {
            int timeOrder = Long.compare(time, other.time);
            if (timeOrder != 0) {
                return timeOrder;
            }
            return Long.compare(sequence, other.sequence);
        }
    }
}
