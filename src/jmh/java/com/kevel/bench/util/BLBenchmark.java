package com.kevel.bench.util;

import com.kevel.util.BL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks the current {@link BL} variant families.
 *
 * <p>Method naming mirrors the API surface: unsuffixed benchmark names exercise the default BL
 * entry points, {@code JDK} suffixes exercise scalar JDK-backed variants, and {@code Unsafe}
 * suffixes exercise the subtraction-based kernels with caller-provided overflow bounds.
 * Saturating and overflow-mask benchmarks are included separately because they provide stronger
 * semantics than wrapped-width arithmetic.
 *
 * <p>Safety requirements: deterministic bounded inputs keep the {@code Unsafe} ordering,
 * max/min/clamp, and absolute-difference kernels inside the subset where subtraction does not
 * overflow. Absolute-value benchmarks still include the full lane distribution, so their unsafe
 * variant intentionally measures wrapped-width behavior on the minimum representable value.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BLBenchmark {
    private static final int SAMPLE_COUNT = 4_096;
    private static final int INT_BOUND = 1 << 20;
    private static final long LONG_BOUND = 1L << 40;
    private static final int INT_CLAMP_LOW = -(INT_BOUND / 2);
    private static final int INT_CLAMP_HIGH = INT_BOUND / 2;
    private static final long LONG_CLAMP_LOW = -(LONG_BOUND / 2);
    private static final long LONG_CLAMP_HIGH = LONG_BOUND / 2;

    /**
     * Per-thread deterministic inputs for BL variant families.
     *
     * <p>The generated ranges are intentionally bounded so subtraction-based {@code Unsafe}
     * comparison, max/min/clamp, and absolute-difference kernels stay within their documented
     * preconditions. Predicate lanes remain one-bit values.
     */
    @State(Scope.Thread)
    public static class BenchmarkState {
        final int[] intLeft = new int[SAMPLE_COUNT];
        final int[] intRight = new int[SAMPLE_COUNT];
        final int[] intPredicate = new int[SAMPLE_COUNT];
        final long[] longLeft = new long[SAMPLE_COUNT];
        final long[] longRight = new long[SAMPLE_COUNT];
        final long[] longPredicate = new long[SAMPLE_COUNT];

        @Setup
        public void setup() {
            long state = 0x9E3779B97F4A7C15L;
            for (int index = 0; index < SAMPLE_COUNT; index++) {
                state = mix(state);
                intLeft[index] = boundedInt(state, INT_BOUND);

                state = mix(state);
                intRight[index] = boundedInt(state, INT_BOUND);

                state = mix(state);
                intPredicate[index] = (int) (state & 1L);

                state = mix(state);
                longLeft[index] = boundedLong(state, LONG_BOUND);

                state = mix(state);
                longRight[index] = boundedLong(state, LONG_BOUND);

                state = mix(state);
                longPredicate[index] = state & 1L;
            }
        }

        private static long mix(long value) {
            long mixed = value + 0x9E3779B97F4A7C15L;
            mixed ^= mixed >>> 30;
            mixed *= 0xBF58476D1CE4E5B9L;
            mixed ^= mixed >>> 27;
            mixed *= 0x94D049BB133111EBL;
            mixed ^= mixed >>> 31;
            return mixed;
        }

        private static int boundedInt(long value, int boundExclusive) {
            return (int) Math.floorMod(value, boundExclusive * 2L) - boundExclusive;
        }

        private static long boundedLong(long value, long boundExclusive) {
            return Math.floorMod(value, boundExclusive * 2L) - boundExclusive;
        }
    }

    /** Returns the accumulated default equality predicate cost for {@code int} lanes. */
    @Benchmark
    public int intEqual(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.equal(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default inequality predicate cost for {@code int} lanes. */
    @Benchmark
    public int intNotEqual(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.notEqual(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default less-than predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intLt(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.lt(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe less-than predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intLtUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.ltUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default greater-than predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intGt(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gt(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe greater-than predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intGtUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gtUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default less-than-or-equal predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intLte(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.lte(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe less-than-or-equal predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intLteUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.lteUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default greater-than-or-equal predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intGte(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gte(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe greater-than-or-equal predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intGteUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gteUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default max-kernel cost for bounded {@code int} lanes. */
    @Benchmark
    public int intMax(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.max(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated JDK max-kernel cost for bounded {@code int} lanes. */
    @Benchmark
    public int intMaxJDK(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.maxJDK(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe max-kernel cost for bounded {@code int} lanes. */
    @Benchmark
    public int intMaxUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.maxUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default min-kernel cost for bounded {@code int} lanes. */
    @Benchmark
    public int intMin(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.min(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated JDK min-kernel cost for bounded {@code int} lanes. */
    @Benchmark
    public int intMinJDK(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.minJDK(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe min-kernel cost for bounded {@code int} lanes. */
    @Benchmark
    public int intMinUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.minUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default clamp cost for bounded {@code int} lanes. */
    @Benchmark
    public int intClamp(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.clamp(state.intLeft[index], INT_CLAMP_LOW, INT_CLAMP_HIGH);
        }
        return checksum;
    }

    /** Returns the accumulated compare-based clamp cost for bounded {@code int} lanes. */
    @Benchmark
    public int intClampJDK(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.clampJDK(state.intLeft[index], INT_CLAMP_LOW, INT_CLAMP_HIGH);
        }
        return checksum;
    }

    /** Returns the accumulated subtraction-only clamp cost for bounded {@code int} lanes. */
    @Benchmark
    public int intClampUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.clampUnsafe(state.intLeft[index], INT_CLAMP_LOW, INT_CLAMP_HIGH);
        }
        return checksum;
    }

    /** Returns the accumulated default zero predicate cost for {@code int} lanes. */
    @Benchmark
    public int intIsZero(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isZero(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default non-zero predicate cost for {@code int} lanes. */
    @Benchmark
    public int intIsNotZero(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isNotZero(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default positive predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intIsPositive(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isPositive(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe positive predicate cost for bounded {@code int} lanes. */
    @Benchmark
    public int intIsPositiveUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isPositiveUnsafe(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default negative predicate cost for {@code int} lanes. */
    @Benchmark
    public int intIsNegative(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isNegative(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default even predicate cost for {@code int} lanes. */
    @Benchmark
    public int intIsEven(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isEven(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default odd predicate cost for {@code int} lanes. */
    @Benchmark
    public int intIsOdd(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isOdd(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default select cost for {@code int} lanes. */
    @Benchmark
    public int intSelect(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.select(state.intPredicate[index], state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default equality-select cost for {@code int} lanes. */
    @Benchmark
    public int intEqualRetX(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.equalRetX(state.intLeft[index], state.intRight[index], state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated JDK absolute-value cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsJDK(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absJDK(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe absolute-value cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absUnsafe(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default saturating absolute-value cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsSaturating(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absSaturating(state.intLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated BL overflow-signaling absolute-value cost for {@code int} lanes. */
    @Benchmark
    public int intAbsWithOverflowMask(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            BL.IntWithOverflowMask result = BL.absWithOverflowMask(state.intLeft[index]);
            checksum += result.value() ^ result.overflowMask();
        }
        return checksum;
    }

    /** Returns the accumulated JDK absolute-difference cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsDiffJDK(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absDiffJDK(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe absolute-difference cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsDiffUnsafe(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absDiffUnsafe(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default saturating absolute-difference cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsDiffSaturating(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absDiffSaturating(state.intLeft[index], state.intRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated BL overflow-signaling absolute-difference cost for bounded {@code int} lanes. */
    @Benchmark
    public int intAbsDiffWithOverflowMask(BenchmarkState state) {
        int checksum = 0;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            BL.IntWithOverflowMask result = BL.absDiffWithOverflowMask(state.intLeft[index], state.intRight[index]);
            checksum += result.value() ^ result.overflowMask();
        }
        return checksum;
    }

    /** Returns the accumulated default equality predicate cost for {@code long} lanes. */
    @Benchmark
    public long longEqual(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.equal(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default inequality predicate cost for {@code long} lanes. */
    @Benchmark
    public long longNotEqual(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.notEqual(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default less-than predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longLt(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.lt(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated subtraction-only less-than predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longLtUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.ltUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default greater-than predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longGt(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gt(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe greater-than predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longGtUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gtUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default less-than-or-equal predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longLte(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.lte(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe less-than-or-equal predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longLteUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.lteUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default greater-than-or-equal predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longGte(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gte(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe greater-than-or-equal predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longGteUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.gteUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default max-kernel cost for bounded {@code long} lanes. */
    @Benchmark
    public long longMax(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.max(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated compare-based max-kernel cost for bounded {@code long} lanes. */
    @Benchmark
    public long longMaxJDK(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.maxJDK(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated subtraction-only max-kernel cost for bounded {@code long} lanes. */
    @Benchmark
    public long longMaxUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.maxUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default min-kernel cost for bounded {@code long} lanes. */
    @Benchmark
    public long longMin(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.min(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated JDK min-kernel cost for bounded {@code long} lanes. */
    @Benchmark
    public long longMinJDK(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.minJDK(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe min-kernel cost for bounded {@code long} lanes. */
    @Benchmark
    public long longMinUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.minUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default clamp cost for bounded {@code long} lanes. */
    @Benchmark
    public long longClamp(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.clamp(state.longLeft[index], LONG_CLAMP_LOW, LONG_CLAMP_HIGH);
        }
        return checksum;
    }

    /** Returns the accumulated compare-based clamp cost for bounded {@code long} lanes. */
    @Benchmark
    public long longClampJDK(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.clampJDK(state.longLeft[index], LONG_CLAMP_LOW, LONG_CLAMP_HIGH);
        }
        return checksum;
    }

    /** Returns the accumulated subtraction-only clamp cost for bounded {@code long} lanes. */
    @Benchmark
    public long longClampUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.clampUnsafe(state.longLeft[index], LONG_CLAMP_LOW, LONG_CLAMP_HIGH);
        }
        return checksum;
    }

    /** Returns the accumulated default zero predicate cost for {@code long} lanes. */
    @Benchmark
    public long longIsZero(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isZero(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default non-zero predicate cost for {@code long} lanes. */
    @Benchmark
    public long longIsNotZero(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isNotZero(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default positive predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longIsPositive(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isPositive(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe positive predicate cost for bounded {@code long} lanes. */
    @Benchmark
    public long longIsPositiveUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isPositiveUnsafe(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default negative predicate cost for {@code long} lanes. */
    @Benchmark
    public long longIsNegative(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isNegative(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default even predicate cost for {@code long} lanes. */
    @Benchmark
    public long longIsEven(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isEven(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default odd predicate cost for {@code long} lanes. */
    @Benchmark
    public long longIsOdd(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.isOdd(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default select cost for {@code long} lanes. */
    @Benchmark
    public long longSelect(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.select(state.longPredicate[index], state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default equality-select cost for {@code long} lanes. */
    @Benchmark
    public long longEqualRetX(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.equalRetX(
                    state.longLeft[index], state.longRight[index], state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated JDK absolute-value cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsJDK(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absJDK(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe absolute-value cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absUnsafe(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default saturating absolute-value cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsSaturating(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absSaturating(state.longLeft[index]);
        }
        return checksum;
    }

    /** Returns the accumulated BL overflow-signaling absolute-value cost for {@code long} lanes. */
    @Benchmark
    public long longAbsWithOverflowMask(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            BL.LongWithOverflowMask result = BL.absWithOverflowMask(state.longLeft[index]);
            checksum += result.value() ^ result.overflowMask();
        }
        return checksum;
    }

    /** Returns the accumulated JDK absolute-difference cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsDiffJDK(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absDiffJDK(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated unsafe absolute-difference cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsDiffUnsafe(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absDiffUnsafe(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated default saturating absolute-difference cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsDiffSaturating(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            checksum += BL.absDiffSaturating(state.longLeft[index], state.longRight[index]);
        }
        return checksum;
    }

    /** Returns the accumulated BL overflow-signaling absolute-difference cost for bounded {@code long} lanes. */
    @Benchmark
    public long longAbsDiffWithOverflowMask(BenchmarkState state) {
        long checksum = 0L;
        for (int index = 0; index < SAMPLE_COUNT; index++) {
            BL.LongWithOverflowMask result = BL.absDiffWithOverflowMask(state.longLeft[index], state.longRight[index]);
            checksum += result.value() ^ result.overflowMask();
        }
        return checksum;
    }
}
