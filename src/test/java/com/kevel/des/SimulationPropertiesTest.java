package com.kevel.des;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SimulationPropertiesTest {

    @Property
    void modelBasedStateMachine_matchesReferenceModel(@ForAll("commands") List<Command> commands) {
        Simulation<Integer> sut = Simulation.create(0);
        RefModel model = new RefModel(0);

        for (Command command : commands) {
            command.applyAndAssert(sut, model);
            assertEquivalentState(sut, model);
        }
    }

    @Property
    void scheduleAt_pastTimeAlwaysThrows(@ForAll("smallNonNegative") int delta) {
        Simulation<Integer> sut = Simulation.create(0);
        sut.scheduleAt(10 + delta, x -> x + 1);
        sut.step();

        assertThrows(IllegalArgumentException.class, () -> sut.scheduleAt(sut.now() - 1, x -> x));
    }

    @Property
    void scheduleIn_negativeDelayAlwaysThrows(@ForAll("positiveDelays") int delay) {
        Simulation<Integer> sut = Simulation.create(0);
        assertThrows(IllegalArgumentException.class, () -> sut.scheduleIn(-delay, x -> x));
    }

    @Property
    void scheduleIn_overflowAlwaysThrows(@ForAll("smallNonNegative") int delta) {
        Simulation<Integer> sut = Simulation.create(0);
        sut.scheduleAt(Long.MAX_VALUE - delta, x -> x);
        sut.step();

        assertThrows(ArithmeticException.class, () -> sut.scheduleIn(delta + 1L, x -> x));
    }

    @Property
    void deterministicReplay_producesIdenticalTraces(@ForAll("scheduleProgram") List<ScheduleCommand> program) {
        Simulation<Integer> left = Simulation.create(0);
        Simulation<Integer> right = Simulation.create(0);

        for (ScheduleCommand command : program) {
            command.apply(left);
            command.apply(right);
        }

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

    @Provide
    Arbitrary<List<Command>> commands() {
        Arbitrary<ScheduleAtOffset> at = Combinators.combine(Arbitraries.longs().between(0, 20), actionSpecs())
                .as(ScheduleAtOffset::new);

        Arbitrary<ScheduleInDelay> in = Combinators.combine(Arbitraries.longs().between(0, 20), actionSpecs())
                .as(ScheduleInDelay::new);

        Arbitrary<RunUntilTimeOffset> untilTime =
                Arbitraries.longs().between(0, 20).map(RunUntilTimeOffset::new);

        Arbitrary<Command> anyCommand = Arbitraries.oneOf(
                at.map(x -> (Command) x),
                in.map(x -> (Command) x),
                Arbitraries.of(new PeekCommand()),
                Arbitraries.of(new StepIfAvailableCommand()),
                untilTime.map(x -> (Command) x),
                Arbitraries.of(new RunAllCommand()));

        return anyCommand.list().ofMinSize(1).ofMaxSize(120);
    }

    @Provide
    Arbitrary<List<ScheduleCommand>> scheduleProgram() {
        Arbitrary<ScheduleAtOffsetOnly> at = Combinators.combine(
                        Arbitraries.longs().between(0, 50), actionSpecs())
                .as(ScheduleAtOffsetOnly::new);
        Arbitrary<ScheduleInOnly> in = Combinators.combine(Arbitraries.longs().between(0, 50), actionSpecs())
                .as(ScheduleInOnly::new);

        return Arbitraries.oneOf(at.map(x -> (ScheduleCommand) x), in.map(x -> (ScheduleCommand) x))
                .list()
                .ofMinSize(1)
                .ofMaxSize(80);
    }

    @Provide
    Arbitrary<Integer> smallNonNegative() {
        return Arbitraries.integers().between(0, 100);
    }

    @Provide
    Arbitrary<Integer> positiveDelays() {
        return Arbitraries.integers().between(1, 1000);
    }

    private static Arbitrary<ActionSpec> actionSpecs() {
        return Arbitraries.of(ActionSpec.values());
    }

    private static void assertEquivalentState(Simulation<Integer> sut, RefModel model) {
        assertEquals(model.now, sut.now());
        assertEquals(model.state, sut.state());
        assertEquals(model.isEmpty(), sut.isEmpty());

        Optional<Event<Integer>> sutNext = sut.peekNext();
        Optional<RefEvent> modelNext = model.peekNext();
        assertEquals(modelNext.isPresent(), sutNext.isPresent());
        if (sutNext.isPresent() && modelNext.isPresent()) {
            assertEquals(modelNext.get().time, sutNext.get().time());
        }
    }

    private enum ActionSpec {
        ADD_1(x -> x + 1),
        ADD_2(x -> x + 2),
        SUB_1(x -> x - 1),
        MUL_2(x -> x * 2),
        NEGATE(x -> -x),
        IDENTITY(x -> x);

        private final UnaryOperator<Integer> op;

        ActionSpec(UnaryOperator<Integer> op) {
            this.op = op;
        }

        int apply(int input) {
            return op.apply(input);
        }

        UnaryOperator<Integer> operator() {
            return op;
        }
    }

    private interface Command {
        void applyAndAssert(Simulation<Integer> sut, RefModel model);
    }

    private interface ScheduleCommand {
        void apply(Simulation<Integer> simulation);
    }

    private record ScheduleAtOffset(long offset, ActionSpec action) implements Command {
        @Override
        public void applyAndAssert(Simulation<Integer> sut, RefModel model) {
            long targetTime = Math.addExact(model.now, offset);
            EventId sutId = sut.scheduleAt(targetTime, action.operator());
            EventId modelId = model.scheduleAt(targetTime, action);
            assertEquals(modelId, sutId);
        }
    }

    private record ScheduleInDelay(long delay, ActionSpec action) implements Command {
        @Override
        public void applyAndAssert(Simulation<Integer> sut, RefModel model) {
            EventId sutId = sut.scheduleIn(delay, action.operator());
            EventId modelId = model.scheduleAt(Math.addExact(model.now, delay), action);
            assertEquals(modelId, sutId);
        }
    }

    private record PeekCommand() implements Command {
        @Override
        public void applyAndAssert(Simulation<Integer> sut, RefModel model) {
            Optional<Event<Integer>> sutPeek = sut.peekNext();
            Optional<RefEvent> modelPeek = model.peekNext();
            assertEquals(modelPeek.isPresent(), sutPeek.isPresent());
            if (sutPeek.isPresent() && modelPeek.isPresent()) {
                assertEquals(modelPeek.get().time, sutPeek.get().time());
            }
        }
    }

    private record StepIfAvailableCommand() implements Command {
        @Override
        public void applyAndAssert(Simulation<Integer> sut, RefModel model) {
            if (model.isEmpty()) {
                assertThrows(IllegalStateException.class, sut::step);
                return;
            }

            StepResult<Integer> sutStep = sut.step();
            RefStep modelStep = model.step();
            assertStepMatchesModel(sutStep, modelStep);
        }
    }

    private record RunUntilTimeOffset(long offset) implements Command {
        @Override
        public void applyAndAssert(Simulation<Integer> sut, RefModel model) {
            long tEnd = Math.addExact(model.now, offset);
            RunResult<Integer> sutResult = sut.runUntilTime(tEnd);
            List<RefStep> modelSteps = model.runUntilTime(tEnd);
            assertRunMatchesModel(sutResult, modelSteps, model.state, model.now);
        }
    }

    private record RunAllCommand() implements Command {
        @Override
        public void applyAndAssert(Simulation<Integer> sut, RefModel model) {
            RunResult<Integer> sutResult = sut.run();
            List<RefStep> modelSteps = model.runAll();
            assertRunMatchesModel(sutResult, modelSteps, model.state, model.now);
            assertTrue(sut.isEmpty());
            assertTrue(model.isEmpty());
        }
    }

    private record ScheduleAtOffsetOnly(long offset, ActionSpec action) implements ScheduleCommand {
        @Override
        public void apply(Simulation<Integer> simulation) {
            simulation.scheduleAt(Math.addExact(simulation.now(), offset), action.operator());
        }
    }

    private record ScheduleInOnly(long delay, ActionSpec action) implements ScheduleCommand {
        @Override
        public void apply(Simulation<Integer> simulation) {
            simulation.scheduleIn(delay, action.operator());
        }
    }

    private static void assertStepMatchesModel(StepResult<Integer> sutStep, RefStep modelStep) {
        assertEquals(modelStep.previousTime, sutStep.previousTime());
        assertEquals(modelStep.newTime, sutStep.newTime());
        assertEquals(modelStep.previousState, sutStep.previousState());
        assertEquals(modelStep.newState, sutStep.newState());
    }

    private static void assertRunMatchesModel(
            RunResult<Integer> result, List<RefStep> expectedSteps, int expectedFinalState, long expectedEndTime) {
        assertEquals(expectedSteps.size(), result.stepsExecuted());
        assertEquals(expectedSteps.size(), result.stepResults().size());
        assertEquals(expectedFinalState, result.finalState());
        assertEquals(expectedEndTime, result.endTime());

        for (int i = 0; i < expectedSteps.size(); i++) {
            StepResult<Integer> step = result.stepResults().get(i);
            RefStep refStep = expectedSteps.get(i);
            assertEquals(refStep.newTime, step.newTime());
            assertEquals(refStep.newState, step.newState());
            assertEquals(refStep.previousTime, step.previousTime());
            assertEquals(refStep.previousState, step.previousState());
        }
    }

    private static final class RefModel {
        private static final Comparator<RefEvent> REF_EVENT_ORDER =
                Comparator.comparingLong((RefEvent e) -> e.time).thenComparingLong(e -> e.sequence);

        private long now;
        private int state;
        private long nextSequence;
        private long nextEventId;
        private final TreeMap<Long, List<RefEvent>> pendingByTime;

        private RefModel(int initialState) {
            this.now = 0L;
            this.state = initialState;
            this.nextSequence = 0L;
            this.nextEventId = 0L;
            this.pendingByTime = new TreeMap<>();
        }

        private EventId scheduleAt(long time, ActionSpec action) {
            RefEvent event = new RefEvent(time, nextSequence++, new EventId(nextEventId++), action);
            pendingByTime.computeIfAbsent(time, ignored -> new ArrayList<>()).add(event);
            return event.id;
        }

        private boolean isEmpty() {
            return pendingByTime.isEmpty();
        }

        private Optional<RefEvent> peekNext() {
            if (pendingByTime.isEmpty()) {
                return Optional.empty();
            }

            long firstTime = pendingByTime.firstKey();
            List<RefEvent> events = pendingByTime.get(firstTime);
            if (events == null || events.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(events.getFirst());
        }

        private RefStep step() {
            RefEvent event = popNext();
            long previousTime = now;
            int previousState = state;
            now = event.time;
            state = event.action.apply(state);
            return new RefStep(previousTime, now, previousState, state);
        }

        private List<RefStep> runUntilTime(long tEnd) {
            List<RefStep> steps = new ArrayList<>();
            while (!pendingByTime.isEmpty()) {
                RefEvent next = peekNext().orElseThrow();
                if (next.time > tEnd) {
                    break;
                }
                steps.add(step());
            }
            return steps;
        }

        private List<RefStep> runAll() {
            List<RefStep> steps = new ArrayList<>();
            while (!pendingByTime.isEmpty()) {
                steps.add(step());
            }
            return steps;
        }

        private RefEvent popNext() {
            if (pendingByTime.isEmpty()) {
                throw new IllegalStateException("reference queue empty");
            }

            long firstTime = pendingByTime.firstKey();
            List<RefEvent> events = pendingByTime.get(firstTime);
            if (events == null || events.isEmpty()) {
                throw new IllegalStateException("reference queue corrupted");
            }

            RefEvent next = events.removeFirst();
            if (events.isEmpty()) {
                pendingByTime.remove(firstTime);
            }
            return next;
        }
    }

    private static final class RefEvent {
        private final long time;
        private final long sequence;
        private final EventId id;
        private final ActionSpec action;

        private RefEvent(long time, long sequence, EventId id, ActionSpec action) {
            this.time = time;
            this.sequence = sequence;
            this.id = id;
            this.action = action;
        }
    }

    private static final class RefStep {
        private final long previousTime;
        private final long newTime;
        private final int previousState;
        private final int newState;

        private RefStep(long previousTime, long newTime, int previousState, int newState) {
            this.previousTime = previousTime;
            this.newTime = newTime;
            this.previousState = previousState;
            this.newState = newState;
        }
    }
}
