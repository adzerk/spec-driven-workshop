package com.kevel.bench.state;

import static com.kevel.bench.state.StateMachineBenchmarkSupport.ROBUST_SEED;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.ROBUST_TRANSITIONS;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.consumeIntersection;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.consumeIntersectionPacked;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.PrimitivePackedMachines;
import static com.kevel.bench.state.StateMachineBenchmarkSupport.SingletonTypedIntersectionStates;

import com.kevel.bench.state.StateMachineBenchmarkSupport.BenchmarkBase;
import com.kevel.bench.state.StateMachineBenchmarkSupport.BenchmarkState;
import com.kevel.bench.state.StateMachineBenchmarkSupport.BoxIntersections;
import com.kevel.bench.state.StateMachineBenchmarkSupport.FfIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.GrIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.IntersectionPayload;
import com.kevel.bench.state.StateMachineBenchmarkSupport.MutableIntersectionMachine;
import com.kevel.bench.state.StateMachineBenchmarkSupport.RgIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.RrIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.RyIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.SealedSingletonIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.StatelessIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.TypedIntersection;
import com.kevel.bench.state.StateMachineBenchmarkSupport.TypedIntersections;
import com.kevel.bench.state.StateMachineBenchmarkSupport.YrIntersection;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks a richer coupled traffic-light machine.
 *
 * <p>The robust benchmark exists because tiny state machines are useful for isolating costs but do
 * not fully represent real traffic on the JVM. This machine models a coupled intersection with
 * legal product states, deterministic branching from the all-red state, and periodic faults that
 * force the machine into flashing-red recovery. The resulting benchmark stresses:
 *
 * <ul>
 *   <li>branch handling,
 *   <li>payload propagation,
 *   <li>allocation behavior under longer chains, and
 *   <li>the difference between object-based and primitive-packed stateless designs, and
 *   <li>the cost of adding compiler-checked singleton typestate on top of packed primitives, and
 *   <li>the cost of representing product states explicitly.
 * </ul>
 *
 * <p>Performance lessons to look for:
 *
 * <ul>
 *   <li>Mutable baselines often win absolute throughput, but the gap may narrow substantially in
 *       hot loops once the JIT optimizes functional designs.
 *   <li>Product-state designs can remain competitive when transition helpers are tiny and branch
 *       patterns are predictable.
 *   <li>Escape-sensitive variants reveal whether a strong result in a hot loop came from true low
 *       cost or from successful allocation elimination.
 * </ul>
 *
 * <p>High-performance Java advice reflected here:
 *
 * <ul>
 *   <li>Use precomputed control-flow inputs in benchmarks rather than generating random decisions.
 *   <li>Benchmark both branch-heavy and recovery-heavy flows if the production system does both.
 *   <li>Use immutable payloads and stable branch patterns when you want to compare representation
 *       choices rather than contention or synchronization artifacts.
 * </ul>
 */
public class RobustStateMachineBenchmark extends BenchmarkBase {

    /**
     * Mutable coupled-machine baseline.
     *
     * <p>This benchmark exists to show the cost floor for a realistic, branchy machine when state is
     * kept in fields and transitions are applied by mutation. It provides the throughput reference
     * for every more strongly typed alternative.
     */
    @Benchmark
    public int mutableEnum_hotLoop(BenchmarkState state) {
        MutableIntersectionMachine machine = new MutableIntersectionMachine(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                machine.fault();
                machine.restore();
            }
            if (state.robustBranches[index]) {
                machine.northSouthFirst();
            } else {
                machine.eastWestFirst();
            }
            machine.next();
            machine.next();
        }
        return machine.checksum();
    }

    /**
     * Stateless product-state machine with enum state values.
     *
     * <p>This test measures the cost of a functional, stateless implementation when the machine is
     * no longer trivial. The lesson is whether immutability and explicit payload passing remain cheap
     * once branching and recovery transitions are part of the workload.
     */
    @Benchmark
    public int statelessEnum_hotLoop(BenchmarkState state) {
        StatelessIntersection intersection = StatelessIntersection.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = StatelessIntersection.fault(intersection);
                intersection = StatelessIntersection.restore(intersection);
            }
            if (state.robustBranches[index]) {
                intersection = StatelessIntersection.northSouthFirst(intersection);
            } else {
                intersection = StatelessIntersection.eastWestFirst(intersection);
            }
            intersection = StatelessIntersection.next(intersection);
            intersection = StatelessIntersection.next(intersection);
        }
        return intersection.state().ordinal()
                + intersection.payload().faultCount()
                + intersection.payload().eastWestGreenCount()
                + intersection.payload().northSouthGreenCount();
    }

    /**
     * Typestate/product-state implementation with explicit generic transitions.
     *
     * <p>This benchmark exists because the coupled intersection is a strong example for compiler
     * checked branching. The performance question is whether a more expressive type-safe API still
     * lets the JVM optimize the loop aggressively when the control flow is deterministic.
     */
    @Benchmark
    public int typestate_hotLoop(BenchmarkState state) {
        TypedIntersection<StateMachineBenchmarkSupport.RrTag> intersection = TypedIntersections.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = TypedIntersections.restore(TypedIntersections.fault(intersection));
            }
            if (state.robustBranches[index]) {
                var northSouthGreen = TypedIntersections.northSouthFirst(intersection);
                var yellow = TypedIntersections.nextFromNorthSouthGreen(northSouthGreen);
                intersection = TypedIntersections.nextFromNorthSouthYellow(yellow);
            } else {
                var eastWestGreen = TypedIntersections.eastWestFirst(intersection);
                var yellow = TypedIntersections.nextFromEastWestGreen(eastWestGreen);
                intersection = TypedIntersections.nextFromEastWestYellow(yellow);
            }
        }
        return intersection.payload().faultCount()
                + intersection.payload().eastWestGreenCount()
                + intersection.payload().northSouthGreenCount();
    }

    /**
     * Primitive-packed product-state implementation.
     *
     * <p>This benchmark exists to measure a version of the coupled traffic light that keeps the
     * entire machine in one packed {@code long}. It is designed to minimize allocations and expose
     * the best-case performance of a low-level stateless approach on the JVM.
     */
    @Benchmark
    public int primitivePacked_hotLoop(BenchmarkState state) {
        long intersection = PrimitivePackedMachines.robustInit(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = PrimitivePackedMachines.robustRestore(PrimitivePackedMachines.robustFault(intersection));
            }
            if (state.robustBranches[index]) {
                intersection = PrimitivePackedMachines.robustNorthSouthFirst(intersection);
            } else {
                intersection = PrimitivePackedMachines.robustEastWestFirst(intersection);
            }
            intersection = PrimitivePackedMachines.robustNext(intersection);
            intersection = PrimitivePackedMachines.robustNext(intersection);
        }
        return PrimitivePackedMachines.robustChecksum(intersection);
    }

    /**
     * Singleton typestate over a packed-primitive carrier.
     *
     * <p>This benchmark exists to test whether compiler-checked product-state transitions can remain
     * close to the primitive-packed performance floor. The packed {@code long} carries the runtime
     * data, while singleton state objects provide typed legal transitions with no per-transition
     * wrapper allocation.
     */
    @Benchmark
    public int singletonTypestatePacked_hotLoop(BenchmarkState state) {
        var ref = state.robustPackedRef;
        var intersection = SingletonTypedIntersectionStates.init(ref, ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = SingletonTypedIntersectionStates.fault(ref).restore(ref);
            }
            if (state.robustBranches[index]) {
                var northSouthGreen = intersection.northSouthFirst(ref);
                var yellow = northSouthGreen.nextState(ref);
                intersection = yellow.nextState(ref);
            } else {
                var eastWestGreen = intersection.eastWestFirst(ref);
                var yellow = eastWestGreen.nextState(ref);
                intersection = yellow.nextState(ref);
            }
        }
        return PrimitivePackedMachines.robustChecksum(ref.value);
    }

    /**
     * Box-backed typestate/product-state implementation.
     *
     * <p>This is the repository-specific robust benchmark. It shows whether using {@code Box<Tag,
     * T>} as a lightweight capability and product-state wrapper remains competitive once the machine
     * carries non-trivial payload and takes multiple legal branches.
     */
    @Benchmark
    public int boxTypestate_hotLoop(BenchmarkState state) {
        var intersection = BoxIntersections.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = BoxIntersections.restore(BoxIntersections.fault(intersection));
            }
            if (state.robustBranches[index]) {
                var northSouthGreen = BoxIntersections.northSouthFirst(intersection);
                var yellow = BoxIntersections.nextFromNorthSouthGreen(northSouthGreen);
                intersection = BoxIntersections.nextFromNorthSouthYellow(yellow);
            } else {
                var eastWestGreen = BoxIntersections.eastWestFirst(intersection);
                var yellow = BoxIntersections.nextFromEastWestGreen(eastWestGreen);
                intersection = BoxIntersections.nextFromEastWestYellow(yellow);
            }
        }
        return intersection.get().faultCount()
                + intersection.get().eastWestGreenCount()
                + intersection.get().northSouthGreenCount();
    }

    /**
     * Sealed product-state implementation with distinct classes.
     *
     * <p>This benchmark exists because the coupled traffic light is the strongest domain example for
     * sealed product states. The benchmark quantifies the runtime price of the most explicit and
     * readable representation when every branch and recovery path creates new state instances.
     */
    @Benchmark
    public int sealed_hotLoop(BenchmarkState state) {
        RrIntersection intersection = new RrIntersection(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                FfIntersection faulted = new FfIntersection(IntersectionPayload.faulted(intersection.payload()));
                intersection = faulted.restore();
            }
            if (state.robustBranches[index]) {
                GrIntersection northSouthGreen = intersection.northSouthFirst();
                YrIntersection yellow = northSouthGreen.nextState();
                intersection = yellow.nextState();
            } else {
                RgIntersection eastWestGreen = intersection.eastWestFirst();
                RyIntersection yellow = eastWestGreen.nextState();
                intersection = yellow.nextState();
            }
        }
        return intersection.payload().faultCount()
                + intersection.payload().eastWestGreenCount()
                + intersection.payload().northSouthGreenCount();
    }

    /**
     * Sealed product-state machine with singleton state objects.
     *
     * <p>This benchmark exists to separate two costs that are often conflated: explicit product-state
     * modeling and per-transition state object allocation. The singleton-state variant keeps the
     * explicit state machine structure while reducing pressure from data-free state instances.
     */
    @Benchmark
    public int sealedSingleton_hotLoop(BenchmarkState state) {
        SealedSingletonIntersection intersection = SealedSingletonIntersection.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = intersection.fault().restore();
            }
            if (state.robustBranches[index]) {
                intersection = intersection.northSouthFirst().next().next();
            } else {
                intersection = intersection.eastWestFirst().next().next();
            }
        }
        return intersection.checksum();
    }

    /**
     * Mutable baseline with forced escape.
     *
     * <p>The robust escape benchmarks force payload snapshots into a preallocated array. This turns a
     * benchmark that might otherwise mostly exercise JIT cleverness into one that reveals the real
     * cost of object production and persistence.
     */
    @Benchmark
    public void mutableEnum_escape(BenchmarkState state, Blackhole hole) {
        MutableIntersectionMachine machine = new MutableIntersectionMachine(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                machine.fault();
                machine.restore();
            }
            if (state.robustBranches[index]) {
                machine.northSouthFirst();
            } else {
                machine.eastWestFirst();
            }
            machine.next();
            machine.next();
            state.robustSink[index] = machine.payload();
        }
        for (IntersectionPayload payload : state.robustSink) {
            consumeIntersection(hole, payload);
        }
    }

    /**
     * Stateless enum with forced escape.
     *
     * <p>This test highlights how much of the stateless functional result was due to allocation
     * elimination. If the hot-loop result is close to mutable but this one allocates heavily, the
     * lesson is that the design is JVM-friendly but not allocation-free.
     */
    @Benchmark
    public void statelessEnum_escape(BenchmarkState state, Blackhole hole) {
        StatelessIntersection intersection = StatelessIntersection.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = StatelessIntersection.fault(intersection);
                intersection = StatelessIntersection.restore(intersection);
            }
            if (state.robustBranches[index]) {
                intersection = StatelessIntersection.northSouthFirst(intersection);
            } else {
                intersection = StatelessIntersection.eastWestFirst(intersection);
            }
            intersection = StatelessIntersection.next(intersection);
            intersection = StatelessIntersection.next(intersection);
            state.robustSink[index] = intersection.payload();
        }
        for (IntersectionPayload payload : state.robustSink) {
            consumeIntersection(hole, payload);
        }
    }

    /**
     * Typestate/product-state with forced escape.
     *
     * <p>This benchmark matters because robust product-state typestate APIs are attractive on paper.
     * The escape case shows the cost when those wrappers survive beyond the local scope and cannot be
     * entirely scalar-replaced by the JVM.
     */
    @Benchmark
    public void typestate_escape(BenchmarkState state, Blackhole hole) {
        TypedIntersection<StateMachineBenchmarkSupport.RrTag> intersection = TypedIntersections.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = TypedIntersections.restore(TypedIntersections.fault(intersection));
            }
            if (state.robustBranches[index]) {
                var northSouthGreen = TypedIntersections.northSouthFirst(intersection);
                var yellow = TypedIntersections.nextFromNorthSouthGreen(northSouthGreen);
                intersection = TypedIntersections.nextFromNorthSouthYellow(yellow);
            } else {
                var eastWestGreen = TypedIntersections.eastWestFirst(intersection);
                var yellow = TypedIntersections.nextFromEastWestGreen(eastWestGreen);
                intersection = TypedIntersections.nextFromEastWestYellow(yellow);
            }
            state.robustSink[index] = intersection.payload();
        }
        for (IntersectionPayload payload : state.robustSink) {
            consumeIntersection(hole, payload);
        }
    }

    /**
     * Primitive-packed product-state implementation with forced escape.
     *
     * <p>This benchmark keeps the packed representation honest by writing the machine state into a
     * primitive array. Unlike object-based escape cases, the representation still avoids heap object
     * churn for the state itself and shows what low-level stateless Java can do under observation.
     */
    @Benchmark
    public void primitivePacked_escape(BenchmarkState state, Blackhole hole) {
        long intersection = PrimitivePackedMachines.robustInit(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = PrimitivePackedMachines.robustRestore(PrimitivePackedMachines.robustFault(intersection));
            }
            if (state.robustBranches[index]) {
                intersection = PrimitivePackedMachines.robustNorthSouthFirst(intersection);
            } else {
                intersection = PrimitivePackedMachines.robustEastWestFirst(intersection);
            }
            intersection = PrimitivePackedMachines.robustNext(intersection);
            intersection = PrimitivePackedMachines.robustNext(intersection);
            state.robustPackedSink[index] = intersection;
        }
        for (long packedIntersection : state.robustPackedSink) {
            consumeIntersectionPacked(hole, packedIntersection);
        }
    }

    /**
     * Singleton typestate over a packed carrier with forced escape.
     *
     * <p>This benchmark measures the practical cost of singleton typestate when every iteration must
     * publish its state. Even in the escape case, the representation stays primitive and should keep
     * GC pressure near zero while preserving typed transitions.
     */
    @Benchmark
    public void singletonTypestatePacked_escape(BenchmarkState state, Blackhole hole) {
        var ref = state.robustPackedRef;
        var intersection = SingletonTypedIntersectionStates.init(ref, ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = SingletonTypedIntersectionStates.fault(ref).restore(ref);
            }
            if (state.robustBranches[index]) {
                var northSouthGreen = intersection.northSouthFirst(ref);
                var yellow = northSouthGreen.nextState(ref);
                intersection = yellow.nextState(ref);
            } else {
                var eastWestGreen = intersection.eastWestFirst(ref);
                var yellow = eastWestGreen.nextState(ref);
                intersection = yellow.nextState(ref);
            }
            state.robustPackedSink[index] = ref.value;
        }
        for (long packedIntersection : state.robustPackedSink) {
            consumeIntersectionPacked(hole, packedIntersection);
        }
    }

    /**
     * Box-backed product-state with forced escape.
     *
     * <p>This benchmark exists to capture the real allocation profile of the repository's lightweight
     * capability wrapper under a larger machine. It is the best answer to "what happens if these
     * values must actually live long enough to be observed?"
     */
    @Benchmark
    public void boxTypestate_escape(BenchmarkState state, Blackhole hole) {
        var intersection = BoxIntersections.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = BoxIntersections.restore(BoxIntersections.fault(intersection));
            }
            if (state.robustBranches[index]) {
                var northSouthGreen = BoxIntersections.northSouthFirst(intersection);
                var yellow = BoxIntersections.nextFromNorthSouthGreen(northSouthGreen);
                intersection = BoxIntersections.nextFromNorthSouthYellow(yellow);
            } else {
                var eastWestGreen = BoxIntersections.eastWestFirst(intersection);
                var yellow = BoxIntersections.nextFromEastWestGreen(eastWestGreen);
                intersection = BoxIntersections.nextFromEastWestYellow(yellow);
            }
            state.robustSink[index] = intersection.get();
        }
        for (IntersectionPayload payload : state.robustSink) {
            consumeIntersection(hole, payload);
        }
    }

    /**
     * Sealed product states with forced escape.
     *
     * <p>This test demonstrates the allocation-heavy side of the cleanest domain model. When the
     * produced values escape, the JVM has far less freedom to erase the modeling cost. This result is
     * especially important for systems that queue, batch, or store state objects between stages.
     */
    @Benchmark
    public void sealed_escape(BenchmarkState state, Blackhole hole) {
        RrIntersection intersection = new RrIntersection(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                FfIntersection faulted = new FfIntersection(IntersectionPayload.faulted(intersection.payload()));
                intersection = faulted.restore();
            }
            if (state.robustBranches[index]) {
                GrIntersection northSouthGreen = intersection.northSouthFirst();
                YrIntersection yellow = northSouthGreen.nextState();
                intersection = yellow.nextState();
            } else {
                RgIntersection eastWestGreen = intersection.eastWestFirst();
                RyIntersection yellow = eastWestGreen.nextState();
                intersection = yellow.nextState();
            }
            state.robustSink[index] = intersection.payload();
        }
        for (IntersectionPayload payload : state.robustSink) {
            consumeIntersection(hole, payload);
        }
    }

    /**
     * Sealed singleton-state variant with forced escape.
     *
     * <p>This benchmark exists to show whether singleton states recover most of the gap between the
     * explicit sealed model and the mutable baseline. If it does, the performance lesson is that much
     * of the cost came from state-object churn rather than the product-state abstraction itself.
     */
    @Benchmark
    public void sealedSingleton_escape(BenchmarkState state, Blackhole hole) {
        SealedSingletonIntersection intersection = SealedSingletonIntersection.init(ROBUST_SEED);
        for (int index = 0; index < ROBUST_TRANSITIONS; index++) {
            if (state.robustFaults[index]) {
                intersection = intersection.fault().restore();
            }
            if (state.robustBranches[index]) {
                intersection = intersection.northSouthFirst().next().next();
            } else {
                intersection = intersection.eastWestFirst().next().next();
            }
            state.robustSink[index] = intersection.payload();
        }
        for (IntersectionPayload payload : state.robustSink) {
            consumeIntersection(hole, payload);
        }
    }
}
