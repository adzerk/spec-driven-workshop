package com.kevel.bench.state;

import com.kevel.util.Box;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Shared fixture and implementation types for the state machine benchmarks.
 *
 * <p>The support code intentionally uses simple, final, immutable payloads and static final
 * transition helpers so the benchmark classes can focus on comparing state representation
 * strategies rather than unrelated implementation noise. The benchmark suite is meant to answer
 * two practical questions:
 *
 * <ul>
 *   <li>How much raw cost comes from the chosen state-machine encoding?
 *   <li>How much of that apparent cost disappears once the JIT compiler inlines, scalar-replaces,
 *       or eliminates allocations in a hot steady-state loop?
 * </ul>
 *
 * <p>General high-performance Java tips reflected here:
 *
 * <ul>
 *   <li>Prefer tiny final or static final helpers on hot paths. This makes inlining easier.
 *   <li>Use immutable payloads when measuring transition mechanics. Mutation can obscure the cost
 *       of the state encoding itself.
 *   <li>Benchmark a "hot loop" and an "escape" scenario separately. Allocation-heavy designs can
 *       look deceptively cheap when escape analysis removes the wrappers.
 *   <li>Keep branch patterns deterministic. Random number generation inside the benchmark would
 *       dominate the measurement.
 * </ul>
 */
public final class StateMachineBenchmarkSupport {
    private StateMachineBenchmarkSupport() {}

    static final int SIMPLE_TRANSITIONS = 1_024;
    static final int ROBUST_TRANSITIONS = 2_048;
    static final int BRANCH_PATTERN_MASK = 0x3F;
    static final SimpleOrderPayload SIMPLE_SEED = new SimpleOrderPayload(7L, 2_500, 1);
    static final IntersectionPayload ROBUST_SEED = new IntersectionPayload(17L, 0, 0, 0, true);

    static final BoxOrderBoxing BOX_HELPER = new BoxOrderBoxing();

    /**
     * Common JMH settings for all state-machine benchmarks.
     *
     * <p>The numbers here aim for reasonably stable results without making the local development
     * loop excessively slow. A separate benchmark run can always override them on the command line
     * for deeper analysis.
     */
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = 5, time = 1)
    @Measurement(iterations = 5, time = 1)
    @Fork(1)
    abstract static class BenchmarkBase {}

    /**
     * Shared per-thread fixture.
     *
     * <p>Using thread-scoped state avoids synchronization and models the most common use of a
     * state machine on a request, workflow, or local computation. The precomputed branch arrays
     * ensure every implementation sees exactly the same control-flow decisions.
     */
    @State(Scope.Thread)
    public static class BenchmarkState {
        final boolean[] robustBranches = new boolean[ROBUST_TRANSITIONS];
        final boolean[] robustFaults = new boolean[ROBUST_TRANSITIONS];
        final SimpleOrderPayload[] simpleSink = new SimpleOrderPayload[SIMPLE_TRANSITIONS];
        final IntersectionPayload[] robustSink = new IntersectionPayload[ROBUST_TRANSITIONS];

        @Setup
        public void setup() {
            for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
                robustBranches[index] = (index & 1) == 0;
                robustFaults[index] = (index & BRANCH_PATTERN_MASK) == BRANCH_PATTERN_MASK;
            }
        }
    }

    record SimpleOrderPayload(long id, int cents, int revision) {
        static final SimpleOrderPayload reset(SimpleOrderPayload payload) {
            return new SimpleOrderPayload(payload.id(), payload.cents(), payload.revision() + 1);
        }
    }

    record IntersectionPayload(long cycles, int northSouthGreenCount, int eastWestGreenCount, int faultCount,
            boolean powerOn) {
        static final IntersectionPayload northSouthGreen(IntersectionPayload payload) {
            return new IntersectionPayload(
                    payload.cycles() + 1,
                    payload.northSouthGreenCount() + 1,
                    payload.eastWestGreenCount(),
                    payload.faultCount(),
                    true);
        }

        static final IntersectionPayload eastWestGreen(IntersectionPayload payload) {
            return new IntersectionPayload(
                    payload.cycles() + 1,
                    payload.northSouthGreenCount(),
                    payload.eastWestGreenCount() + 1,
                    payload.faultCount(),
                    true);
        }

        static final IntersectionPayload yellow(IntersectionPayload payload) {
            return new IntersectionPayload(
                    payload.cycles(),
                    payload.northSouthGreenCount(),
                    payload.eastWestGreenCount(),
                    payload.faultCount(),
                    true);
        }

        static final IntersectionPayload red(IntersectionPayload payload) {
            return new IntersectionPayload(
                    payload.cycles(),
                    payload.northSouthGreenCount(),
                    payload.eastWestGreenCount(),
                    payload.faultCount(),
                    true);
        }

        static final IntersectionPayload faulted(IntersectionPayload payload) {
            return new IntersectionPayload(
                    payload.cycles(),
                    payload.northSouthGreenCount(),
                    payload.eastWestGreenCount(),
                    payload.faultCount() + 1,
                    false);
        }

        static final IntersectionPayload restored(IntersectionPayload payload) {
            return new IntersectionPayload(
                    payload.cycles(),
                    payload.northSouthGreenCount(),
                    payload.eastWestGreenCount(),
                    payload.faultCount(),
                    true);
        }
    }

    enum MutableOrderState {
        DRAFT,
        PAID,
        SHIPPED
    }

    static final class MutableOrderMachine {
        private MutableOrderState state;
        private SimpleOrderPayload payload;

        MutableOrderMachine(SimpleOrderPayload payload) {
            this.state = MutableOrderState.DRAFT;
            this.payload = payload;
        }

        final void pay() {
            state = MutableOrderState.PAID;
        }

        final void ship() {
            state = MutableOrderState.SHIPPED;
        }

        final void reset() {
            state = MutableOrderState.DRAFT;
            payload = SimpleOrderPayload.reset(payload);
        }

        final SimpleOrderPayload payload() {
            return payload;
        }

        final int checksum() {
            return state.ordinal() + payload.revision();
        }
    }

    enum StatelessOrderState {
        DRAFT,
        PAID,
        SHIPPED
    }

    record StatelessOrder(SimpleOrderPayload payload, StatelessOrderState state) {
        static final StatelessOrder create(SimpleOrderPayload payload) {
            return new StatelessOrder(payload, StatelessOrderState.DRAFT);
        }

        static final StatelessOrder pay(StatelessOrder order) {
            return new StatelessOrder(order.payload(), StatelessOrderState.PAID);
        }

        static final StatelessOrder ship(StatelessOrder order) {
            return new StatelessOrder(order.payload(), StatelessOrderState.SHIPPED);
        }

        static final StatelessOrder reset(StatelessOrder order) {
            return new StatelessOrder(SimpleOrderPayload.reset(order.payload()), StatelessOrderState.DRAFT);
        }
    }

    interface DraftTag {}

    interface PaidTag {}

    interface ShippedTag {}

    static final class TypedOrder<S> {
        private final SimpleOrderPayload payload;

        TypedOrder(SimpleOrderPayload payload) {
            this.payload = payload;
        }

        final SimpleOrderPayload payload() {
            return payload;
        }
    }

    static final class TypedOrders {
        private TypedOrders() {}

        static final TypedOrder<DraftTag> create(SimpleOrderPayload payload) {
            return new TypedOrder<>(payload);
        }

        static final TypedOrder<PaidTag> pay(TypedOrder<DraftTag> order) {
            return new TypedOrder<>(order.payload());
        }

        static final TypedOrder<ShippedTag> ship(TypedOrder<PaidTag> order) {
            return new TypedOrder<>(order.payload());
        }

        static final TypedOrder<DraftTag> reset(TypedOrder<ShippedTag> order) {
            return new TypedOrder<>(SimpleOrderPayload.reset(order.payload()));
        }
    }

    static final class BoxOrderBoxing {
        private BoxOrderBoxing() {}

        final Box<DraftTag, SimpleOrderPayload> create(SimpleOrderPayload payload) {
            return Box.of(payload);
        }

        final Box<PaidTag, SimpleOrderPayload> pay(Box<DraftTag, SimpleOrderPayload> order) {
            return order.into();
        }

        final Box<ShippedTag, SimpleOrderPayload> ship(Box<PaidTag, SimpleOrderPayload> order) {
            return order.into();
        }

        final Box<DraftTag, SimpleOrderPayload> reset(Box<ShippedTag, SimpleOrderPayload> order) {
            return Box.of(SimpleOrderPayload.reset(order.get()));
        }
    }

    sealed interface SealedOrderState permits SealedDraftOrder, SealedPaidOrder, SealedShippedOrder {
        SimpleOrderPayload payload();
    }

    record SealedDraftOrder(SimpleOrderPayload payload) implements SealedOrderState {
        final SealedPaidOrder pay() {
            return new SealedPaidOrder(payload);
        }
    }

    record SealedPaidOrder(SimpleOrderPayload payload) implements SealedOrderState {
        final SealedShippedOrder ship() {
            return new SealedShippedOrder(payload);
        }
    }

    record SealedShippedOrder(SimpleOrderPayload payload) implements SealedOrderState {
        final SealedDraftOrder reset() {
            return new SealedDraftOrder(SimpleOrderPayload.reset(payload));
        }
    }

    enum MutableIntersectionState {
        GR,
        YR,
        RR,
        RG,
        RY,
        FF
    }

    static final class MutableIntersectionMachine {
        private MutableIntersectionState state;
        private IntersectionPayload payload;

        MutableIntersectionMachine(IntersectionPayload payload) {
            this.state = MutableIntersectionState.RR;
            this.payload = payload;
        }

        final void northSouthFirst() {
            state = MutableIntersectionState.GR;
            payload = IntersectionPayload.northSouthGreen(payload);
        }

        final void eastWestFirst() {
            state = MutableIntersectionState.RG;
            payload = IntersectionPayload.eastWestGreen(payload);
        }

        final void next() {
            state = switch (state) {
                case GR -> {
                    payload = IntersectionPayload.yellow(payload);
                    yield MutableIntersectionState.YR;
                }
                case YR -> {
                    payload = IntersectionPayload.red(payload);
                    yield MutableIntersectionState.RR;
                }
                case RG -> {
                    payload = IntersectionPayload.yellow(payload);
                    yield MutableIntersectionState.RY;
                }
                case RY -> {
                    payload = IntersectionPayload.red(payload);
                    yield MutableIntersectionState.RR;
                }
                case RR, FF -> state;
            };
        }

        final void fault() {
            state = MutableIntersectionState.FF;
            payload = IntersectionPayload.faulted(payload);
        }

        final void restore() {
            state = MutableIntersectionState.RR;
            payload = IntersectionPayload.restored(payload);
        }

        final int checksum() {
            return state.ordinal() + payload.faultCount() + payload.eastWestGreenCount() + payload.northSouthGreenCount();
        }

        final IntersectionPayload payload() {
            return payload;
        }
    }

    record StatelessIntersection(IntersectionPayload payload, MutableIntersectionState state) {
        static final StatelessIntersection init(IntersectionPayload payload) {
            return new StatelessIntersection(payload, MutableIntersectionState.RR);
        }

        static final StatelessIntersection northSouthFirst(StatelessIntersection intersection) {
            return new StatelessIntersection(
                    IntersectionPayload.northSouthGreen(intersection.payload()), MutableIntersectionState.GR);
        }

        static final StatelessIntersection eastWestFirst(StatelessIntersection intersection) {
            return new StatelessIntersection(
                    IntersectionPayload.eastWestGreen(intersection.payload()), MutableIntersectionState.RG);
        }

        static final StatelessIntersection next(StatelessIntersection intersection) {
            return switch (intersection.state()) {
                case GR -> new StatelessIntersection(
                        IntersectionPayload.yellow(intersection.payload()), MutableIntersectionState.YR);
                case YR -> new StatelessIntersection(
                        IntersectionPayload.red(intersection.payload()), MutableIntersectionState.RR);
                case RG -> new StatelessIntersection(
                        IntersectionPayload.yellow(intersection.payload()), MutableIntersectionState.RY);
                case RY -> new StatelessIntersection(
                        IntersectionPayload.red(intersection.payload()), MutableIntersectionState.RR);
                case RR, FF -> intersection;
            };
        }

        static final StatelessIntersection fault(StatelessIntersection intersection) {
            return new StatelessIntersection(IntersectionPayload.faulted(intersection.payload()), MutableIntersectionState.FF);
        }

        static final StatelessIntersection restore(StatelessIntersection intersection) {
            return new StatelessIntersection(IntersectionPayload.restored(intersection.payload()), MutableIntersectionState.RR);
        }
    }

    interface GrTag {}

    interface YrTag {}

    interface RrTag {}

    interface RgTag {}

    interface RyTag {}

    interface FfTag {}

    static final class TypedIntersection<S> {
        private final IntersectionPayload payload;

        TypedIntersection(IntersectionPayload payload) {
            this.payload = payload;
        }

        final IntersectionPayload payload() {
            return payload;
        }
    }

    static final class TypedIntersections {
        private TypedIntersections() {}

        static final TypedIntersection<RrTag> init(IntersectionPayload payload) {
            return new TypedIntersection<>(payload);
        }

        static final TypedIntersection<GrTag> northSouthFirst(TypedIntersection<RrTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.northSouthGreen(intersection.payload()));
        }

        static final TypedIntersection<RgTag> eastWestFirst(TypedIntersection<RrTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.eastWestGreen(intersection.payload()));
        }

        static final TypedIntersection<YrTag> nextFromNorthSouthGreen(TypedIntersection<GrTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.yellow(intersection.payload()));
        }

        static final TypedIntersection<RrTag> nextFromNorthSouthYellow(TypedIntersection<YrTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.red(intersection.payload()));
        }

        static final TypedIntersection<RyTag> nextFromEastWestGreen(TypedIntersection<RgTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.yellow(intersection.payload()));
        }

        static final TypedIntersection<RrTag> nextFromEastWestYellow(TypedIntersection<RyTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.red(intersection.payload()));
        }

        static final TypedIntersection<FfTag> fault(TypedIntersection<?> intersection) {
            return new TypedIntersection<>(IntersectionPayload.faulted(intersection.payload()));
        }

        static final TypedIntersection<RrTag> restore(TypedIntersection<FfTag> intersection) {
            return new TypedIntersection<>(IntersectionPayload.restored(intersection.payload()));
        }
    }

    static final class BoxIntersections {
        private BoxIntersections() {}

        static final Box<RrTag, IntersectionPayload> init(IntersectionPayload payload) {
            return Box.of(payload);
        }

        static final Box<GrTag, IntersectionPayload> northSouthFirst(Box<RrTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.northSouthGreen(intersection.get()));
        }

        static final Box<RgTag, IntersectionPayload> eastWestFirst(Box<RrTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.eastWestGreen(intersection.get()));
        }

        static final Box<YrTag, IntersectionPayload> nextFromNorthSouthGreen(Box<GrTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.yellow(intersection.get()));
        }

        static final Box<RrTag, IntersectionPayload> nextFromNorthSouthYellow(Box<YrTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.red(intersection.get()));
        }

        static final Box<RyTag, IntersectionPayload> nextFromEastWestGreen(Box<RgTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.yellow(intersection.get()));
        }

        static final Box<RrTag, IntersectionPayload> nextFromEastWestYellow(Box<RyTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.red(intersection.get()));
        }

        static final Box<FfTag, IntersectionPayload> fault(Box<?, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.faulted(intersection.get()));
        }

        static final Box<RrTag, IntersectionPayload> restore(Box<FfTag, IntersectionPayload> intersection) {
            return Box.of(IntersectionPayload.restored(intersection.get()));
        }
    }

    sealed interface SealedIntersectionState permits GrIntersection, YrIntersection, RrIntersection, RgIntersection,
            RyIntersection, FfIntersection {
        IntersectionPayload payload();
    }

    record GrIntersection(IntersectionPayload payload) implements SealedIntersectionState {
        final YrIntersection nextState() {
            return new YrIntersection(IntersectionPayload.yellow(payload));
        }
    }

    record YrIntersection(IntersectionPayload payload) implements SealedIntersectionState {
        final RrIntersection nextState() {
            return new RrIntersection(IntersectionPayload.red(payload));
        }
    }

    record RrIntersection(IntersectionPayload payload) implements SealedIntersectionState {
        final GrIntersection northSouthFirst() {
            return new GrIntersection(IntersectionPayload.northSouthGreen(payload));
        }

        final RgIntersection eastWestFirst() {
            return new RgIntersection(IntersectionPayload.eastWestGreen(payload));
        }
    }

    record RgIntersection(IntersectionPayload payload) implements SealedIntersectionState {
        final RyIntersection nextState() {
            return new RyIntersection(IntersectionPayload.yellow(payload));
        }
    }

    record RyIntersection(IntersectionPayload payload) implements SealedIntersectionState {
        final RrIntersection nextState() {
            return new RrIntersection(IntersectionPayload.red(payload));
        }
    }

    record FfIntersection(IntersectionPayload payload) implements SealedIntersectionState {
        final RrIntersection restore() {
            return new RrIntersection(IntersectionPayload.restored(payload));
        }
    }

    static final class SealedSingletonIntersection {
        private final IntersectionPayload payload;
        private final SingletonState state;

        private SealedSingletonIntersection(IntersectionPayload payload, SingletonState state) {
            this.payload = payload;
            this.state = state;
        }

        static final SealedSingletonIntersection init(IntersectionPayload payload) {
            return new SealedSingletonIntersection(payload, RedRed.INSTANCE);
        }

        final SealedSingletonIntersection northSouthFirst() {
            return new SealedSingletonIntersection(IntersectionPayload.northSouthGreen(payload), GreenRed.INSTANCE);
        }

        final SealedSingletonIntersection eastWestFirst() {
            return new SealedSingletonIntersection(IntersectionPayload.eastWestGreen(payload), RedGreen.INSTANCE);
        }

        final SealedSingletonIntersection next() {
            return state.next(payload);
        }

        final SealedSingletonIntersection fault() {
            return new SealedSingletonIntersection(IntersectionPayload.faulted(payload), FlashingFlashing.INSTANCE);
        }

        final SealedSingletonIntersection restore() {
            return new SealedSingletonIntersection(IntersectionPayload.restored(payload), RedRed.INSTANCE);
        }

        final int checksum() {
            return state.stateId() + payload.faultCount() + payload.eastWestGreenCount() + payload.northSouthGreenCount();
        }

        final IntersectionPayload payload() {
            return payload;
        }
    }

    sealed interface SingletonState permits GreenRed, YellowRed, RedRed, RedGreen, RedYellow, FlashingFlashing {
        SealedSingletonIntersection next(IntersectionPayload payload);

        int stateId();
    }

    enum GreenRed implements SingletonState {
        INSTANCE;

        @Override
        public SealedSingletonIntersection next(IntersectionPayload payload) {
            return new SealedSingletonIntersection(IntersectionPayload.yellow(payload), YellowRed.INSTANCE);
        }

        @Override
        public int stateId() {
            return 0;
        }
    }

    enum YellowRed implements SingletonState {
        INSTANCE;

        @Override
        public SealedSingletonIntersection next(IntersectionPayload payload) {
            return new SealedSingletonIntersection(IntersectionPayload.red(payload), RedRed.INSTANCE);
        }

        @Override
        public int stateId() {
            return 1;
        }
    }

    enum RedRed implements SingletonState {
        INSTANCE;

        @Override
        public SealedSingletonIntersection next(IntersectionPayload payload) {
            return new SealedSingletonIntersection(payload, this);
        }

        @Override
        public int stateId() {
            return 2;
        }
    }

    enum RedGreen implements SingletonState {
        INSTANCE;

        @Override
        public SealedSingletonIntersection next(IntersectionPayload payload) {
            return new SealedSingletonIntersection(IntersectionPayload.yellow(payload), RedYellow.INSTANCE);
        }

        @Override
        public int stateId() {
            return 3;
        }
    }

    enum RedYellow implements SingletonState {
        INSTANCE;

        @Override
        public SealedSingletonIntersection next(IntersectionPayload payload) {
            return new SealedSingletonIntersection(IntersectionPayload.red(payload), RedRed.INSTANCE);
        }

        @Override
        public int stateId() {
            return 4;
        }
    }

    enum FlashingFlashing implements SingletonState {
        INSTANCE;

        @Override
        public SealedSingletonIntersection next(IntersectionPayload payload) {
            return new SealedSingletonIntersection(payload, this);
        }

        @Override
        public int stateId() {
            return 5;
        }
    }

    static final void consumeSimple(Blackhole hole, SimpleOrderPayload payload) {
        hole.consume(payload.id());
        hole.consume(payload.cents());
        hole.consume(payload.revision());
    }

    static final void consumeIntersection(Blackhole hole, IntersectionPayload payload) {
        hole.consume(payload.cycles());
        hole.consume(payload.northSouthGreenCount());
        hole.consume(payload.eastWestGreenCount());
        hole.consume(payload.faultCount());
        hole.consume(payload.powerOn());
    }
}
