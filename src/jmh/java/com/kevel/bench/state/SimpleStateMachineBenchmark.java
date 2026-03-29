package com.kevel.bench.state;

import static com.kevel.bench.state.StateMachineBenchmarkSupport.BOX_HELPER;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.PrimitivePackedMachines;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.SIMPLE_SEED;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.SIMPLE_TRANSITIONS;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.consumeSimple;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.consumeSimplePacked;

import com.kevel.bench.state.StateMachineBenchmarkSupport.BenchmarkBase;
import com.kevel.bench.state.StateMachineBenchmarkSupport.BenchmarkState;
import com.kevel.bench.state.StateMachineBenchmarkSupport.BoxOrderBoxing;
import com.kevel.bench.state.StateMachineBenchmarkSupport.MutableOrderMachine;
import com.kevel.bench.state.StateMachineBenchmarkSupport.SealedDraftOrder;
import com.kevel.bench.state.StateMachineBenchmarkSupport.SealedPaidOrder;
import com.kevel.bench.state.StateMachineBenchmarkSupport.SealedShippedOrder;
import com.kevel.bench.state.StateMachineBenchmarkSupport.SimpleOrderPayload;
import com.kevel.bench.state.StateMachineBenchmarkSupport.StatelessOrder;
import com.kevel.bench.state.StateMachineBenchmarkSupport.TypedOrder;
import com.kevel.bench.state.StateMachineBenchmarkSupport.TypedOrders;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks a small three-state machine using several implementation techniques.
 *
 * <p>The machine is intentionally tiny: {@code Draft -> Paid -> Shipped -> Draft}. This keeps the
 * work per transition small enough that representation cost is visible. The small machine answers
 * the practical question most teams start with: "What is the raw cost of getting stronger type
 * guarantees?"
 *
 * <p>Each benchmark exists for a specific performance lesson:
 *
 * <ul>
 *   <li>{@code mutableEnum_hotLoop}: the baseline for a mutable runtime-checked design.
 *   <li>{@code statelessEnum_hotLoop}: isolates the cost of a stateless functional style.
 *   <li>{@code typestate_hotLoop}: shows whether wrapper allocations disappear in a hot loop when
 *       the JVM can apply escape analysis.
 *   <li>{@code primitivePacked_hotLoop}: measures a low-level packed-long implementation that aims
 *       to minimize allocations and maximize throughput.
 *   <li>{@code boxTypestate_hotLoop}: measures the lightweight tagged-box approach used in this
 *       repository.
 *   <li>{@code sealed_hotLoop}: captures the cost of explicit state classes with fresh instances.
 *   <li>{@code *_escape}: forces intermediate values into arrays so allocations and object churn
 *       are visible even when the JIT would otherwise optimize them away.
 * </ul>
 *
 * <p>General implementation advice for high-performance Java code:
 *
 * <ul>
 *   <li>Keep hot-path helpers tiny and final so HotSpot can inline them aggressively.
 *   <li>Separate allocation-sensitive paths from hot steady-state loops when benchmarking.
 *   <li>Measure a simple machine and a richer machine. Tiny loops are good for isolating costs but
 *       can exaggerate effects that disappear in more realistic work.
 * </ul>
 */
public class SimpleStateMachineBenchmark extends BenchmarkBase {

    /**
     * Baseline mutable implementation.
     *
     * <p>This benchmark exists to measure the minimum overhead of a practical Java implementation
     * that stores state in a mutable field. If stronger abstractions are dramatically slower than
     * this result, the gap is real. If they are close, the safety may be worth the small cost.
     */
    @Benchmark
    public int mutableEnum_hotLoop() {
        MutableOrderMachine machine = new MutableOrderMachine(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            machine.pay();
            machine.ship();
            machine.reset();
        }
        return machine.checksum();
    }

    /**
     * Stateless functional enum implementation.
     *
     * <p>This benchmark measures the cost of returning new immutable values instead of mutating a
     * machine object. In hot code, the JVM may eliminate some wrapper churn. In colder code, the
     * allocations can remain real. The benchmark is useful because teams often choose stateless
     * designs for simplicity and thread-safety.
     */
    @Benchmark
    public int statelessEnum_hotLoop() {
        StatelessOrder order = StatelessOrder.create(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            order = StatelessOrder.pay(order);
            order = StatelessOrder.ship(order);
            order = StatelessOrder.reset(order);
        }
        return order.state().ordinal() + order.payload().revision();
    }

    /**
     * Typestate implementation with a direct generic wrapper.
     *
     * <p>This test exists because typestate is often the most attractive "compiler-checked without
     * runtime tags" option in Java. The lesson here is that generic state markers are erased, so
     * the runtime cost is not from generics themselves. The real question is whether the JVM can
     * optimize away wrapper allocations in a hot loop.
     */
    @Benchmark
    public int typestate_hotLoop() {
        TypedOrder<StateMachineBenchmarkSupport.DraftTag> draft = TypedOrders.create(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            TypedOrder<StateMachineBenchmarkSupport.PaidTag> paid = TypedOrders.pay(draft);
            TypedOrder<StateMachineBenchmarkSupport.ShippedTag> shipped = TypedOrders.ship(paid);
            draft = TypedOrders.reset(shipped);
        }
        return draft.payload().revision();
    }

    /**
     * Primitive-packed stateless implementation.
     *
     * <p>This benchmark exists to measure the style most likely to minimize runtime overhead: one
     * packed {@code long}, primitive field updates, and {@code static final} transition helpers. It
     * is the strongest baseline for teams optimizing for throughput and low allocation while still
     * keeping the transition logic explicit.
     */
    @Benchmark
    public int primitivePacked_hotLoop() {
        long order = PrimitivePackedMachines.simpleCreate(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            order = PrimitivePackedMachines.simplePay(order);
            order = PrimitivePackedMachines.simpleShip(order);
            order = PrimitivePackedMachines.simpleReset(order);
        }
        return PrimitivePackedMachines.simpleChecksum(order);
    }

    /**
     * Typestate using the repository's {@link com.kevel.util.Box} helper.
     *
     * <p>This benchmark shows whether a lightweight tagged wrapper is meaningfully different from a
     * custom typestate wrapper. The performance lesson is that a well-designed utility abstraction
     * can be "good enough" if it preserves a simple, immutable object shape.
     */
    @Benchmark
    public int boxTypestate_hotLoop() {
        BoxOrderBoxing helper = BOX_HELPER;
        var draft = helper.create(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            var paid = helper.pay(draft);
            var shipped = helper.ship(paid);
            draft = helper.reset(shipped);
        }
        return draft.get().revision();
    }

    /**
     * Sealed explicit-state implementation.
     *
     * <p>This test exists because sealed classes are often the clearest domain model. The machine is
     * easy to reason about, but each transition produces a fresh state object. This benchmark helps
     * quantify the cost of that clarity in a minimal setting.
     */
    @Benchmark
    public int sealed_hotLoop() {
        SealedDraftOrder draft = new SealedDraftOrder(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            SealedPaidOrder paid = draft.pay();
            SealedShippedOrder shipped = paid.ship();
            draft = shipped.reset();
        }
        return draft.payload().revision();
    }

    /**
     * Mutable baseline with forced escape.
     *
     * <p>This benchmark stores payload snapshots into a preallocated array. The goal is to keep the
     * benchmark honest by preventing the JVM from proving that the loop result is entirely local and
     * disposable. Use this result when you care about object churn that survives beyond a single hot
     * method.
     */
    @Benchmark
    public void mutableEnum_escape(BenchmarkState state, Blackhole hole) {
        MutableOrderMachine machine = new MutableOrderMachine(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            machine.pay();
            machine.ship();
            machine.reset();
            state.simpleSink[step] = machine.payload();
        }
        for (SimpleOrderPayload payload : state.simpleSink) {
            consumeSimple(hole, payload);
        }
    }

    /**
     * Stateless enum with forced escape.
     *
     * <p>In a hot loop the JVM may optimize the wrapper away. This benchmark exists to show the cost
     * when the produced states must survive long enough to be observed. The difference between this
     * result and the hot-loop result is a useful proxy for how much escape analysis helped.
     */
    @Benchmark
    public void statelessEnum_escape(BenchmarkState state, Blackhole hole) {
        StatelessOrder order = StatelessOrder.create(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            order = StatelessOrder.pay(order);
            order = StatelessOrder.ship(order);
            order = StatelessOrder.reset(order);
            state.simpleSink[step] = order.payload();
        }
        for (SimpleOrderPayload payload : state.simpleSink) {
            consumeSimple(hole, payload);
        }
    }

    /**
     * Typestate wrapper with forced escape.
     *
     * <p>This benchmark is the allocation-sensitive companion to {@code typestate_hotLoop}. If the
     * hot-loop result is very strong but this one allocates heavily, the lesson is not that typestate
     * is free, but that it is very friendly to JVM optimizations in tightly scoped code.
     */
    @Benchmark
    public void typestate_escape(BenchmarkState state, Blackhole hole) {
        TypedOrder<StateMachineBenchmarkSupport.DraftTag> draft = TypedOrders.create(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            TypedOrder<StateMachineBenchmarkSupport.PaidTag> paid = TypedOrders.pay(draft);
            TypedOrder<StateMachineBenchmarkSupport.ShippedTag> shipped = TypedOrders.ship(paid);
            draft = TypedOrders.reset(shipped);
            state.simpleSink[step] = draft.payload();
        }
        for (SimpleOrderPayload payload : state.simpleSink) {
            consumeSimple(hole, payload);
        }
    }

    /**
     * Primitive-packed stateless implementation with forced escape.
     *
     * <p>This benchmark shows what happens when a packed primitive representation must survive
     * beyond the hot method. Even here, the implementation avoids object allocation by writing into
     * a primitive {@code long[]} sink rather than an object array.
     */
    @Benchmark
    public void primitivePacked_escape(BenchmarkState state, Blackhole hole) {
        long order = PrimitivePackedMachines.simpleCreate(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            order = PrimitivePackedMachines.simplePay(order);
            order = PrimitivePackedMachines.simpleShip(order);
            order = PrimitivePackedMachines.simpleReset(order);
            state.simplePackedSink[step] = order;
        }
        for (long packedOrder : state.simplePackedSink) {
            consumeSimplePacked(hole, packedOrder);
        }
    }

    /**
     * Box-backed typestate with forced escape.
     *
     * <p>This is the real-world counterpart to the direct typestate benchmark for this repository.
     * It exists to show whether using a reusable generic helper adds meaningful runtime cost once the
     * values must persist beyond the current method.
     */
    @Benchmark
    public void boxTypestate_escape(BenchmarkState state, Blackhole hole) {
        BoxOrderBoxing helper = BOX_HELPER;
        var draft = helper.create(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            var paid = helper.pay(draft);
            var shipped = helper.ship(paid);
            draft = helper.reset(shipped);
            state.simpleSink[step] = draft.get();
        }
        for (SimpleOrderPayload payload : state.simpleSink) {
            consumeSimple(hole, payload);
        }
    }

    /**
     * Sealed state classes with forced escape.
     *
     * <p>This benchmark shows the price of the most explicit model once the allocated objects cannot
     * be optimized away. If this result is materially slower than the mutable baseline, the benefit
     * of sealed-state clarity should be weighed against throughput or allocation budgets.
     */
    @Benchmark
    public void sealed_escape(BenchmarkState state, Blackhole hole) {
        SealedDraftOrder draft = new SealedDraftOrder(SIMPLE_SEED);
        for (int step = 0; step < SIMPLE_TRANSITIONS; step++) {
            SealedPaidOrder paid = draft.pay();
            SealedShippedOrder shipped = paid.ship();
            draft = shipped.reset();
            state.simpleSink[step] = draft.payload();
        }
        for (SimpleOrderPayload payload : state.simpleSink) {
            consumeSimple(hole, payload);
        }
    }
}
