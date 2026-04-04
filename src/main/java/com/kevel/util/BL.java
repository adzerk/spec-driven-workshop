package com.kevel.util;

import com.google.errorprone.annotations.CheckReturnValue;

/**
 * Branchless integer/long primitives with explicit overflow signaling.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>predicate methods return {@code 0}/{@code 1} bits only;
 *   <li>mask helpers and overflow-mask results use {@code 0}/{@code -1} bit masks;
 *   <li>methods are pure, allocation-free on the hot path, and deterministic;
 *   <li>ordering methods remain correct at signed integer boundaries.
 * </ul>
 *
 * <p>Safety requirements: these methods trade Java's usual exception-oriented arithmetic behavior
 * for explicit masks that callers can compose in vector-friendly code. Where a mathematically exact
 * result cannot be represented in the same width, this class exposes either a saturating variant or
 * a result paired with an overflow mask.
 *
 * <p>Variant selection:
 *
 * <ul>
 *   <li>Use the default methods when you need signed-correct full-range semantics or any
 *       overflow-aware API, including saturating and overflow-signaling arithmetic;
 *   <li>prefer {@code JDK} variants for scalar {@code abs}, {@code absDiff}, {@code max},
 *       {@code min}, and {@code clamp};
 *   <li>prefer {@code Unsafe} variants when you need more performance and can prove the
 *       documented overflow bounds, particularly for ordering predicates and {@code isPositive};
 *   <li>use overflow-mask and saturating variants only when their stronger semantics are required,
 *       because they are materially more expensive than wrapped-width kernels.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * int clamped = BL.clampJDK(sample, lowerBound, upperBound);
 * int chooseA = BL.select(BL.lt(left, right), left, right);
 * BL.IntWithOverflowMask magnitude = BL.absWithOverflowMask(Integer.MIN_VALUE);
 * int saturated = BL.absSaturating(Integer.MIN_VALUE);
 * }</pre>
 *
 * <p>All methods are thread-safe because the class is stateless.
 */
/*@ code_java_math @*/
public final class BL {

    private BL() {}

    /** Result of an {@code int} arithmetic operation paired with a full-width overflow mask. */
    public record IntWithOverflowMask(int value, int overflowMask) {}

    /** Result of a {@code long} arithmetic operation paired with a full-width overflow mask. */
    public record LongWithOverflowMask(long value, long overflowMask) {}

    /**
     * Returns {@code 1} when {@code left == right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit equality predicate
     */
    /*@ public normal_behavior
      @   ensures left == right ==> \result == 1;
      @   ensures left != right ==> \result == 0;
      @*/
    public static /*@ pure @*/ int equal(final int left, final int right) {
        final int xor = left ^ right;
        return 1 ^ ((xor | -xor) >>> 31);
    }

    /**
     * Returns {@code 1} when {@code left == right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit equality predicate
     */
    /*@ public normal_behavior
      @   ensures left == right ==> \result == 1L;
      @   ensures left != right ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long equal(final long left, final long right) {
        final long xor = left ^ right;
        return 1L ^ ((xor | -xor) >>> 63);
    }

    /**
     * Returns {@code 1} when {@code left != right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit inequality predicate
     */
    /*@ public normal_behavior
      @   ensures left != right ==> \result == 1;
      @   ensures left == right ==> \result == 0;
      @*/
    public static /*@ pure @*/ int notEqual(final int left, final int right) {
        final int xor = left ^ right;
        return (xor | -xor) >>> 31;
    }

    /**
     * Returns {@code 1} when {@code left != right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit inequality predicate
     */
    /*@ public normal_behavior
      @   ensures left != right ==> \result == 1L;
      @   ensures left == right ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long notEqual(final long left, final long right) {
        final long xor = left ^ right;
        return (xor | -xor) >>> 63;
    }

    /**
     * Returns {@code 1} when {@code left < right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code int}
     * range. In current benchmarks it is materially slower than {@link #ltUnsafe(int, int)} on
     * bounded domains, so prefer the unsafe kernel only when the subtraction precondition is proven.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than predicate
     */
    /*@ public normal_behavior
      @   ensures left < right ==> \result == 1;
      @   ensures left >= right ==> \result == 0;
      @*/
    public static /*@ pure @*/ int lt(final int left, final int right) {
        return lessThanMask(left, right) & 1;
    }

    /**
     * Returns {@code 1} when {@code left < right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It is only correct when the
     * caller proves that {@code left - right} does not overflow in two's-complement arithmetic.
     * Typical valid uses are bounded vector kernels where every lane stays inside a tighter domain
     * than the full {@code int} range.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int ltUnsafe(final int left, final int right) {
        return subtractLessThanMaskUnsafe(left, right) & 1;
    }

    /**
     * Returns {@code 1} when {@code left < right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code long}
     * range. In current benchmarks it is materially slower than {@link #ltUnsafe(long, long)} on
     * bounded domains, so prefer the unsafe kernel only when the subtraction precondition is proven.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than predicate
     */
    /*@ public normal_behavior
      @   ensures left < right ==> \result == 1L;
      @   ensures left >= right ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long lt(final long left, final long right) {
        return lessThanMask(left, right) & 1L;
    }

    /**
     * Returns {@code 1} when {@code left < right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It is only correct when the
     * caller proves that {@code left - right} does not overflow in two's-complement arithmetic.
     * Typical valid uses are bounded vector kernels with a tighter caller-controlled numeric range.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long ltUnsafe(final long left, final long right) {
        return subtractLessThanMaskUnsafe(left, right) & 1L;
    }

    /**
     * Returns {@code 1} when {@code left > right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code int}
     * range. In current benchmarks it is materially slower than {@link #gtUnsafe(int, int)} on
     * bounded domains.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than predicate
     */
    /*@ public normal_behavior
      @   ensures left > right ==> \result == 1;
      @   ensures left <= right ==> \result == 0;
      @*/
    public static /*@ pure @*/ int gt(final int left, final int right) {
        return lt(/* left= */ right, /* right= */ left);
    }

    /**
     * Returns {@code 1} when {@code left > right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int gtUnsafe(final int left, final int right) {
        return ltUnsafe(/* left= */ right, /* right= */ left);
    }

    /**
     * Returns {@code 1} when {@code left > right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code long}
     * range. In current benchmarks it is materially slower than {@link #gtUnsafe(long, long)} on
     * bounded domains.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than predicate
     */
    /*@ public normal_behavior
      @   ensures left > right ==> \result == 1L;
      @   ensures left <= right ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long gt(final long left, final long right) {
        return lt(/* left= */ right, /* right= */ left);
    }

    /**
     * Returns {@code 1} when {@code left > right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long gtUnsafe(final long left, final long right) {
        return ltUnsafe(/* left= */ right, /* right= */ left);
    }

    /**
     * Returns {@code 1} when {@code left <= right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code int}
     * range. In current benchmarks it is materially slower than {@link #lteUnsafe(int, int)} on
     * bounded domains.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than-or-equal predicate
     */
    /*@ public normal_behavior
      @   ensures left <= right ==> \result == 1;
      @   ensures left > right ==> \result == 0;
      @*/
    public static /*@ pure @*/ int lte(final int left, final int right) {
        return gt(left, right) ^ 1;
    }

    /**
     * Returns {@code 1} when {@code left <= right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than-or-equal predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int lteUnsafe(final int left, final int right) {
        return gtUnsafe(left, right) ^ 1;
    }

    /**
     * Returns {@code 1} when {@code left <= right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code long}
     * range. In current benchmarks it is materially slower than {@link #lteUnsafe(long, long)} on
     * bounded domains.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than-or-equal predicate
     */
    /*@ public normal_behavior
      @   ensures left <= right ==> \result == 1L;
      @   ensures left > right ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long lte(final long left, final long right) {
        return gt(left, right) ^ 1L;
    }

    /**
     * Returns {@code 1} when {@code left <= right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed less-than-or-equal predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long lteUnsafe(final long left, final long right) {
        return gtUnsafe(left, right) ^ 1L;
    }

    /**
     * Returns {@code 1} when {@code left >= right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code int}
     * range. In current benchmarks it is materially slower than {@link #gteUnsafe(int, int)} on
     * bounded domains.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than-or-equal predicate
     */
    /*@ public normal_behavior
      @   ensures left >= right ==> \result == 1;
      @   ensures left < right ==> \result == 0;
      @*/
    public static /*@ pure @*/ int gte(final int left, final int right) {
        return lt(left, right) ^ 1;
    }

    /**
     * Returns {@code 1} when {@code left >= right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than-or-equal predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int gteUnsafe(final int left, final int right) {
        return ltUnsafe(left, right) ^ 1;
    }

    /**
     * Returns {@code 1} when {@code left >= right}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code long}
     * range. In current benchmarks it is materially slower than {@link #gteUnsafe(long, long)} on
     * bounded domains.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than-or-equal predicate
     */
    /*@ public normal_behavior
      @   ensures left >= right ==> \result == 1L;
      @   ensures left < right ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long gte(final long left, final long right) {
        return lt(left, right) ^ 1L;
    }

    /**
     * Returns {@code 1} when {@code left >= right}; otherwise returns {@code 0}.
     *
     * @param left first value
     * @param right second value
     * @return one-bit signed greater-than-or-equal predicate when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long gteUnsafe(final long left, final long right) {
        return ltUnsafe(left, right) ^ 1L;
    }

    /**
     * Returns {@code 1} when {@code value == 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit zero predicate
     */
    /*@ public normal_behavior
      @   ensures value == 0 ==> \result == 1;
      @   ensures value != 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isZero(final int value) {
        return 1 ^ ((value | -value) >>> 31);
    }

    /**
     * Returns {@code 1} when {@code value == 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit zero predicate
     */
    /*@ public normal_behavior
      @   ensures value == 0L ==> \result == 1L;
      @   ensures value != 0L ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isZero(final long value) {
        return 1L ^ ((value | -value) >>> 63);
    }

    /**
     * Returns {@code 1} when {@code value != 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit non-zero predicate
     */
    /*@ public normal_behavior
      @   ensures value != 0 ==> \result == 1;
      @   ensures value == 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isNotZero(final int value) {
        return (value | -value) >>> 31;
    }

    /**
     * Returns {@code 1} when {@code value != 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit non-zero predicate
     */
    /*@ public normal_behavior
      @   ensures value != 0L ==> \result == 1L;
      @   ensures value == 0L ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isNotZero(final long value) {
        return (value | -value) >>> 63;
    }

    /**
     * Returns {@code 1} when {@code value > 0}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code int}
     * range, including {@code Integer.MIN_VALUE}. In current benchmarks it is slower than
     * {@link #isPositiveUnsafe(int)} on bounded domains.
     *
     * @param value input value
     * @return one-bit positive predicate
     */
    /*@ public normal_behavior
      @   ensures value > 0 ==> \result == 1;
      @   ensures value <= 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isPositive(final int value) {
        return isNotZero(value) & (isNegative(value) ^ 1);
    }

    /**
     * Returns {@code 1} when {@code value > 0}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It matches the legacy
     * subtraction-style kernel, which misclassifies the minimum representable value because
     * negating that value wraps in two's-complement arithmetic.
     *
     * @param value input value
     * @return one-bit positive predicate when {@code -value} stays representable
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int isPositiveUnsafe(final int value) {
        return (-value >>> 31) & 1;
    }

    /**
     * Returns {@code 1} when {@code value > 0}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this default variant is correct across the full signed {@code long}
     * range, including {@code Long.MIN_VALUE}. In current benchmarks it is slower than
     * {@link #isPositiveUnsafe(long)} on bounded domains.
     *
     * @param value input value
     * @return one-bit positive predicate
     */
    /*@ public normal_behavior
      @   ensures value > 0L ==> \result == 1L;
      @   ensures value <= 0L ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isPositive(final long value) {
        return isNotZero(value) & (isNegative(value) ^ 1L);
    }

    /**
     * Returns {@code 1} when {@code value > 0}; otherwise returns {@code 0}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It matches the legacy
     * subtraction-style kernel, which misclassifies the minimum representable value because
     * negating that value wraps in two's-complement arithmetic.
     *
     * @param value input value
     * @return one-bit positive predicate when {@code -value} stays representable
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long isPositiveUnsafe(final long value) {
        return (-value >>> 63) & 1L;
    }

    /**
     * Returns {@code 1} when {@code value < 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit negative predicate
     */
    /*@ public normal_behavior
      @   ensures value < 0 ==> \result == 1;
      @   ensures value >= 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isNegative(final int value) {
        return (value >>> 31) & 1;
    }

    /**
     * Returns {@code 1} when {@code value < 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit negative predicate
     */
    /*@ public normal_behavior
      @   ensures value < 0L ==> \result == 1L;
      @   ensures value >= 0L ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isNegative(final long value) {
        return (value >>> 63) & 1L;
    }

    /**
     * Returns {@code 1} when {@code value >= 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit non-negative predicate
     */
    /*@ public normal_behavior
      @   ensures value >= 0 ==> \result == 1;
      @   ensures value < 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isNonNegative(final int value) {
        return isNegative(value) ^ 1;
    }

    /**
     * Returns {@code 1} when {@code value >= 0}; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit non-negative predicate
     */
    /*@ public normal_behavior
      @   ensures value >= 0L ==> \result == 1L;
      @   ensures value < 0L ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isNonNegative(final long value) {
        return isNegative(value) ^ 1L;
    }

    /**
     * Returns {@code 1} when {@code value} is even; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit even predicate
     */
    /*@ public normal_behavior
      @   ensures value % 2 == 0 ==> \result == 1;
      @   ensures value % 2 != 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isEven(final int value) {
        return (value & 1) ^ 1;
    }

    /**
     * Returns {@code 1} when {@code value} is even; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit even predicate
     */
    /*@ public normal_behavior
      @   ensures value % 2 == 0 ==> \result == 1L;
      @   ensures value % 2 != 0 ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isEven(final long value) {
        return (value & 1L) ^ 1L;
    }

    /**
     * Returns {@code 1} when {@code value} is odd; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit odd predicate
     */
    /*@ public normal_behavior
      @   ensures value % 2 != 0 ==> \result == 1;
      @   ensures value % 2 == 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isOdd(final int value) {
        return value & 1;
    }

    /**
     * Returns {@code 1} when {@code value} is odd; otherwise returns {@code 0}.
     *
     * @param value input value
     * @return one-bit odd predicate
     */
    /*@ public normal_behavior
      @   ensures value % 2 != 0 ==> \result == 1L;
      @   ensures value % 2 == 0 ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isOdd(final long value) {
        return value & 1L;
    }

    /**
     * Returns {@code 1} when {@code value} is zero or a positive power of two; otherwise returns
     * {@code 0}.
     *
     * @param value input value
     * @return one-bit power-of-two-or-zero predicate
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int isPowerOfTwoOrZero(final int value) {
        return isNonNegative(value) & equal(value & (value - 1), 0);
    }

    /**
     * Returns {@code 1} when {@code value} is zero or a positive power of two; otherwise returns
     * {@code 0}.
     *
     * @param value input value
     * @return one-bit power-of-two-or-zero predicate
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long isPowerOfTwoOrZero(final long value) {
        return isNonNegative(value) & equal(value & (value - 1), 0L);
    }

    /**
     * Returns {@code 1} when {@code value} is a strictly positive power of two; otherwise returns
     * {@code 0}.
     *
     * @param value input value
     * @return one-bit strict power-of-two predicate
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @*/
    public static /*@ pure @*/ int isPowerOfTwo(final int value) {
        return isNotZero(value) & isPowerOfTwoOrZero(value);
    }

    /**
     * Returns {@code 1} when {@code value} is a strictly positive power of two; otherwise returns
     * {@code 0}.
     *
     * @param value input value
     * @return one-bit strict power-of-two predicate
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @*/
    public static /*@ pure @*/ long isPowerOfTwo(final long value) {
        return isNotZero(value) & isPowerOfTwoOrZero(value);
    }

    /**
     * Returns {@code 1} when {@code dividend} is evenly divisible by {@code divisor}; otherwise
     * returns {@code 0}.
     *
     * <p>Safety requirements: this method is total and branchless. A zero divisor returns
     * {@code 0} instead of throwing so callers can keep vector lanes live.
     *
     * @param dividend value to test
     * @param divisor divisor to test against
     * @return one-bit divisibility predicate; zero divisor maps to {@code 0}
     * @implNote Remainder instructions are usually harder to vectorize than bitwise kernels. Use a
     *     power-of-two divisor and masking when the divisor domain allows it.
     */
    /*@ public normal_behavior
      @   ensures \result == 0 || \result == 1;
      @   ensures divisor == 0 ==> \result == 0;
      @*/
    public static /*@ pure @*/ int isDivisibleBy(final int dividend, final int divisor) {
        final int divisorPresent = isNotZero(divisor);
        final int safeDivisor = divisor | (divisorPresent ^ 1);
        return divisorPresent & isZero(dividend % safeDivisor);
    }

    /**
     * Returns {@code 1} when {@code dividend} is evenly divisible by {@code divisor}; otherwise
     * returns {@code 0}.
     *
     * <p>Safety requirements: this method is total and branchless. A zero divisor returns
     * {@code 0} instead of throwing so callers can keep vector lanes live.
     *
     * @param dividend value to test
     * @param divisor divisor to test against
     * @return one-bit divisibility predicate; zero divisor maps to {@code 0}
     * @implNote Remainder instructions are usually harder to vectorize than bitwise kernels. Use a
     *     power-of-two divisor and masking when the divisor domain allows it.
     */
    /*@ public normal_behavior
      @   ensures \result == 0L || \result == 1L;
      @   ensures divisor == 0L ==> \result == 0L;
      @*/
    public static /*@ pure @*/ long isDivisibleBy(final long dividend, final long divisor) {
        final long divisorPresent = isNotZero(divisor);
        final long safeDivisor = divisor | (divisorPresent ^ 1L);
        return divisorPresent & isZero(dividend % safeDivisor);
    }

    /**
     * Returns {@code whenTrue} when {@code predicateBit == 1}; otherwise returns {@code whenFalse}.
     *
     * @param predicateBit one-bit predicate; values other than {@code 1} select {@code whenFalse}
     * @param whenTrue selected result for predicate {@code 1}
     * @param whenFalse selected result otherwise
     * @return selected branch value without control-flow branching
     */
    /*@ public normal_behavior
      @   ensures predicateBit == 1 ==> \result == whenTrue;
      @   ensures predicateBit != 1 ==> \result == whenFalse;
      @*/
    public static /*@ pure @*/ int select(final int predicateBit, final int whenTrue, final int whenFalse) {
        return equalRetX(predicateBit, 1, whenTrue, whenFalse);
    }

    /**
     * Returns {@code whenTrue} when {@code predicateBit == 1}; otherwise returns {@code whenFalse}.
     *
     * @param predicateBit one-bit predicate; values other than {@code 1} select {@code whenFalse}
     * @param whenTrue selected result for predicate {@code 1}
     * @param whenFalse selected result otherwise
     * @return selected branch value without control-flow branching
     */
    /*@ public normal_behavior
      @   ensures predicateBit == 1L ==> \result == whenTrue;
      @   ensures predicateBit != 1L ==> \result == whenFalse;
      @*/
    public static /*@ pure @*/ long select(final long predicateBit, final long whenTrue, final long whenFalse) {
        return equalRetX(predicateBit, 1L, whenTrue, whenFalse);
    }

    /**
     * Returns {@code whenEqual} when {@code left == right}; otherwise returns {@code whenNotEqual}.
     *
     * @param left first comparison value
     * @param right second comparison value
     * @param whenEqual selected result when the values match
     * @param whenNotEqual selected result otherwise
     * @return selected branch value without control-flow branching
     */
    /*@ public normal_behavior
      @   ensures left == right ==> \result == whenEqual;
      @   ensures left != right ==> \result == whenNotEqual;
      @*/
    public static /*@ pure @*/ int equalRetX(final int left, final int right, final int whenEqual, final int whenNotEqual) {
        final int mask = equalMask(left, right);
        return whenNotEqual ^ ((whenEqual ^ whenNotEqual) & mask);
    }

    /**
     * Returns {@code whenEqual} when {@code left == right}; otherwise returns {@code whenNotEqual}.
     *
     * @param left first comparison value
     * @param right second comparison value
     * @param whenEqual selected result when the values match
     * @param whenNotEqual selected result otherwise
     * @return selected branch value without control-flow branching
     */
    /*@ public normal_behavior
      @   ensures left == right ==> \result == whenEqual;
      @   ensures left != right ==> \result == whenNotEqual;
      @*/
    public static /*@ pure @*/ long equalRetX(final long left, final long right, final long whenEqual, final long whenNotEqual) {
        final long mask = equalMask(left, right);
        return whenNotEqual ^ ((whenEqual ^ whenNotEqual) & mask);
    }

    /**
     * Returns the larger of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this variant is safe for the full signed {@code int} range and keeps
     * the comparison expressed as an explicit branchless mask. That shape is useful when callers
     * want to compose the same ordering kernel into larger bit-manipulation code, even though the
     * compare-based {@link #maxJDK(int, int)} variant benchmarks much faster in scalar code on this
     * JVM.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed maximum without overflow-sensitive comparisons
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == right;
      @ also
      @   requires right <= left;
      @   ensures \result == left;
      @ |}
      @*/
    public static /*@ pure @*/ int max(final int left, final int right) {
        final int mask = lessThanMask(left, right);
        return left ^ ((left ^ right) & mask);
    }

    /**
     * Returns the larger of {@code left} and {@code right} using JDK comparison intrinsics.
     * The JDK code is written as {@code return (a >= b) ? a : b;} and is not branchless by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant is safe for all signed inputs. In current benchmarks it
     * is the fastest scalar {@code int} max variant on this JVM, and it is included as a practical
     * alternative for callers who prioritize throughput over a strictly hand-written bit kernel.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed maximum
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == right;
      @ also
      @   requires right <= left;
      @   ensures \result == left;
      @ |}
      @*/
    public static /*@ pure @*/ int maxJDK(final int left, final int right) {
        return Math.max(left, right);
    }

    /**
     * Returns the larger of {@code left} and {@code right} using the legacy subtraction mask.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It is only correct when the
     * caller proves that {@code left - right} does not overflow.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed maximum when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == left || \result == right;
      @*/
    public static /*@ pure @*/ int maxUnsafe(final int left, final int right) {
        final int mask = subtractLessThanMaskUnsafe(left, right);
        return left ^ ((left ^ right) & mask);
    }

    /**
     * Returns the larger of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this variant is safe for the full signed {@code long} range and keeps
     * the comparison expressed as an explicit branchless mask. Prefer {@link #maxJDK(long, long)}
     * when scalar throughput matters more than preserving a mask-oriented code shape; current
     * benchmarks show a large gap in favor of the JDK variant.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed maximum without overflow-sensitive comparisons
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == right;
      @ also
      @   requires right <= left;
      @   ensures \result == left;
      @ |}
      @*/
    public static /*@ pure @*/ long max(final long left, final long right) {
        final long mask = lessThanMask(left, right);
        return left ^ ((left ^ right) & mask);
    }

    /**
     * Returns the larger of {@code left} and {@code right} using JDK comparison intrinsics.
     * The JDK code is written as {@code return (a >= b) ? a : b;} and is not branchless by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant is safe for all signed inputs. In current benchmarks it
     * is the fastest scalar {@code long} max variant on this JVM.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed maximum
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == right;
      @ also
      @   requires right <= left;
      @   ensures \result == left;
      @ |}
      @*/
    public static /*@ pure @*/ long maxJDK(final long left, final long right) {
        return Math.max(left, right);
    }

    /**
     * Returns the larger of {@code left} and {@code right} using the legacy subtraction mask.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It is only correct when the
     * caller proves that {@code left - right} does not overflow.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed maximum when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == left || \result == right;
      @*/
    public static /*@ pure @*/ long maxUnsafe(final long left, final long right) {
        final long mask = subtractLessThanMaskUnsafe(left, right);
        return left ^ ((left ^ right) & mask);
    }

    /**
     * Returns the smaller of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this variant is safe for the full signed {@code int} range and keeps
     * the ordering logic branchless and mask-composable. Prefer {@link #minJDK(int, int)} when the
     * call site is ordinary scalar code and HotSpot's intrinsic shaping matters more than explicit
     * bit-kernel structure; current benchmarks show a large gap in favor of the JDK variant.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed minimum without overflow-sensitive comparisons
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == left;
      @ also
      @   requires right <= left;
      @   ensures \result == right;
      @ |}
      @*/
    public static /*@ pure @*/ int min(final int left, final int right) {
        final int mask = lessThanMask(left, right);
        return right ^ ((left ^ right) & mask);
    }

    /**
     * Returns the smaller of {@code left} and {@code right} using JDK comparison intrinsics.
     * The JDK code is written as {@code return (a <= b) ? a : b;} and is not branchless by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant is safe for all signed inputs. In current benchmarks it
     * is the fastest scalar {@code int} min variant on this JVM.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed minimum
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == left;
      @ also
      @   requires right <= left;
      @   ensures \result == right;
      @ |}
      @*/
    public static /*@ pure @*/ int minJDK(final int left, final int right) {
        return Math.min(left, right);
    }

    /**
     * Returns the smaller of {@code left} and {@code right} using the legacy subtraction mask.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It is only correct when the
     * caller proves that {@code left - right} does not overflow.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed minimum when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == left || \result == right;
      @*/
    public static /*@ pure @*/ int minUnsafe(final int left, final int right) {
        final int mask = subtractLessThanMaskUnsafe(left, right);
        return right ^ ((left ^ right) & mask);
    }

    /**
     * Returns the smaller of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this variant is safe for the full signed {@code long} range and keeps
     * the ordering logic branchless and mask-composable. Prefer {@link #minJDK(long, long)} when a
     * scalar-friendly intrinsic is more important than preserving the explicit mask formulation;
     * current benchmarks show a large gap in favor of the JDK variant.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed minimum without overflow-sensitive comparisons
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == left;
      @ also
      @   requires right <= left;
      @   ensures \result == right;
      @ |}
      @*/
    public static /*@ pure @*/ long min(final long left, final long right) {
        final long mask = lessThanMask(left, right);
        return right ^ ((left ^ right) & mask);
    }

    /**
     * Returns the smaller of {@code left} and {@code right} using JDK comparison intrinsics.
     * The JDK code is written as {@code return (a <= b) ? a : b;} and is not branchless by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant is safe for all signed inputs. In current benchmarks it
     * is the fastest scalar {@code long} min variant on this JVM.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed minimum
     */
    /*@ public normal_behavior
      @ {|
      @   requires left <= right;
      @   ensures \result == left;
      @ also
      @   requires right <= left;
      @   ensures \result == right;
      @ |}
      @*/
    public static /*@ pure @*/ long minJDK(final long left, final long right) {
        return Math.min(left, right);
    }

    /**
     * Returns the smaller of {@code left} and {@code right} using the legacy subtraction mask.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It is only correct when the
     * caller proves that {@code left - right} does not overflow.
     *
     * @param left first candidate
     * @param right second candidate
     * @return signed minimum when subtraction stays in range
     */
    /*@ public normal_behavior
      @   ensures \result == left || \result == right;
      @*/
    public static /*@ pure @*/ long minUnsafe(final long left, final long right) {
        final long mask = subtractLessThanMaskUnsafe(left, right);
        return right ^ ((left ^ right) & mask);
    }

    /**
     * Clamps {@code value} to the inclusive range {@code [lowerBound, upperBound]}.
     *
     * <p>Preconditions: {@code lowerBound <= upperBound}.
     *
     * <p>Postconditions: the result is always within the supplied inclusive range when the bound
     * precondition holds.
     *
     * <p>Safety requirements: this variant keeps both comparisons in the same safe mask-oriented
     * style as {@link #max(int, int)} and {@link #min(int, int)}. Prefer {@link #clampJDK(int,
     * int, int)} when scalar throughput matters more than preserving a fully explicit bit kernel;
     * current benchmarks show a large gap in favor of the JDK variant.
     *
     * @param value candidate value
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @return {@code value} clamped to the inclusive range
     */
    /*@ public normal_behavior
      @   requires lowerBound <= upperBound;
      @   ensures \result >= lowerBound && \result <= upperBound;
      @   ensures value >= lowerBound && value <= upperBound ==> \result == value;
      @   ensures value < lowerBound ==> \result == lowerBound;
      @   ensures value > upperBound ==> \result == upperBound;
      @*/
    public static /*@ pure @*/ int clamp(final int value, final int lowerBound, final int upperBound) {
        return min(max(value, lowerBound), upperBound);
    }

    /**
     * Clamps {@code value} to the inclusive range {@code [lowerBound, upperBound]} using the JDK
     * max/min intrinsics.
     * The JDK code is written as {@code return (int) Math.min(max, Math.max(value, min));} and
     * is not branchless by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Preconditions: {@code lowerBound <= upperBound}.
     *
     * <p>Safety requirements: this variant is safe for all signed inputs. In current benchmarks it
     * is the fastest scalar {@code int} clamp variant on this JVM.
     *
     * @param value candidate value
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @return {@code value} clamped to the inclusive range
     */
    /*@ public normal_behavior
      @   requires lowerBound <= upperBound;
      @   ensures \result >= lowerBound && \result <= upperBound;
      @   ensures value >= lowerBound && value <= upperBound ==> \result == value;
      @   ensures value < lowerBound ==> \result == lowerBound;
      @   ensures value > upperBound ==> \result == upperBound;
      @*/
    public static /*@ pure @*/ int clampJDK(final int value, final int lowerBound, final int upperBound) {
        return Math.min(Math.max(value, lowerBound), upperBound);
    }

    /**
     * Clamps {@code value} to the inclusive range {@code [lowerBound, upperBound]} using the legacy
     * subtraction-mask kernels.
     *
     * <p>Preconditions: {@code lowerBound <= upperBound} and both intermediate subtractions
     * {@code value - lowerBound} and {@code max(value, lowerBound) - upperBound} must stay within
     * range.
     *
     * <p>Safety requirements: this variant is intentionally unsafe and exists for callers who can
     * prove tight numeric bounds and want the most vectorization-friendly code shape.
     *
     * @param value candidate value
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @return clamped result when the subtraction preconditions hold
     */
    /*@ public normal_behavior
      @   requires lowerBound <= upperBound;
      @   ensures \result == value || \result == lowerBound || \result == upperBound;
      @*/
    public static /*@ pure @*/ int clampUnsafe(final int value, final int lowerBound, final int upperBound) {
        return minUnsafe(maxUnsafe(value, lowerBound), upperBound);
    }

    /**
     * Clamps {@code value} to the inclusive range {@code [lowerBound, upperBound]}.
     *
     * <p>Preconditions: {@code lowerBound <= upperBound}.
     *
     * <p>Postconditions: the result is always within the supplied inclusive range when the bound
     * precondition holds.
     *
     * <p>Safety requirements: this variant keeps both comparisons in the same safe mask-oriented
     * style as {@link #max(long, long)} and {@link #min(long, long)}. Prefer {@link #clampJDK(long,
     * long, long)} when a scalar-friendly intrinsic is more important than preserving explicit mask
     * composition; current benchmarks show a large gap in favor of the JDK variant.
     *
     * @param value candidate value
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @return {@code value} clamped to the inclusive range
     */
    /*@ public normal_behavior
      @   requires lowerBound <= upperBound;
      @   ensures \result >= lowerBound && \result <= upperBound;
      @   ensures value >= lowerBound && value <= upperBound ==> \result == value;
      @   ensures value < lowerBound ==> \result == lowerBound;
      @   ensures value > upperBound ==> \result == upperBound;
      @*/
    public static /*@ pure @*/ long clamp(final long value, final long lowerBound, final long upperBound) {
        return min(max(value, lowerBound), upperBound);
    }

    /**
     * Clamps {@code value} to the inclusive range {@code [lowerBound, upperBound]} using the JDK
     * max/min intrinsics.
     * The JDK code is written as {@code return Math.min(max, Math.max(value, min));} and
     * is not branchless by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Preconditions: {@code lowerBound <= upperBound}.
     *
     * <p>Safety requirements: this variant is safe for all signed inputs. In current benchmarks it
     * is the fastest scalar {@code long} clamp variant on this JVM.
     *
     * @param value candidate value
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @return {@code value} clamped to the inclusive range
     */
    /*@ public normal_behavior
      @   requires lowerBound <= upperBound;
      @   ensures \result >= lowerBound && \result <= upperBound;
      @   ensures value >= lowerBound && value <= upperBound ==> \result == value;
      @   ensures value < lowerBound ==> \result == lowerBound;
      @   ensures value > upperBound ==> \result == upperBound;
      @*/
    public static /*@ pure @*/ long clampJDK(final long value, final long lowerBound, final long upperBound) {
        return Math.min(Math.max(value, lowerBound), upperBound);
    }

    /**
     * Clamps {@code value} to the inclusive range {@code [lowerBound, upperBound]} using the legacy
     * subtraction-mask kernels.
     *
     * <p>Preconditions: {@code lowerBound <= upperBound} and both intermediate subtractions
     * {@code value - lowerBound} and {@code max(value, lowerBound) - upperBound} must stay within
     * range.
     *
     * <p>Safety requirements: this variant is intentionally unsafe and exists for callers who can
     * prove tight numeric bounds and want the most vectorization-friendly code shape.
     *
     * @param value candidate value
     * @param lowerBound inclusive lower bound
     * @param upperBound inclusive upper bound
     * @return clamped result when the subtraction preconditions hold
     */
    /*@ public normal_behavior
      @   requires lowerBound <= upperBound;
      @   ensures \result == value || \result == lowerBound || \result == upperBound;
      @*/
    public static /*@ pure @*/ long clampUnsafe(final long value, final long lowerBound, final long upperBound) {
        return minUnsafe(maxUnsafe(value, lowerBound), upperBound);
    }

    /**
     * Returns the JDK-style wrapped-width absolute value of {@code value}.
     * The JDK code is written as {@code return (a < 0) ? -a : a;;} and is not branchless
     * by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant delegates to {@link Math#abs(int)}. It preserves JVM
     * same-width arithmetic semantics, including returning {@code Integer.MIN_VALUE} for the single
     * unrepresentable magnitude. In current benchmarks it is the fastest scalar {@code int}
     * absolute-value variant on this JVM. Use {@link #absSaturating(int)} or
     * {@link #absWithOverflowMask(int)} when callers need explicit overflow handling.
     *
     * @param value source value
     * @return JDK-style wrapped-width absolute value
     */
    /*@ public normal_behavior
      @   ensures \result == ( 0 <= value ? value : value == Integer.MIN_VALUE ? Integer.MIN_VALUE : -value);
      @*/
    public static /*@ pure @*/ int absJDK(final int value) {
        return Math.abs(value);
    }

    /**
     * Returns the JDK-style wrapped-width absolute value of {@code value}.
     * The JDK code is written as {@code return (a < 0) ? -a : a;;} and is not branchless
     * by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant delegates to {@link Math#abs(long)}. It preserves JVM
     * same-width arithmetic semantics, including returning {@code Long.MIN_VALUE} for the single
     * unrepresentable magnitude. In current benchmarks it is effectively tied with
     * {@link #absUnsafe(long)} for scalar {@code long} throughput. Use {@link #absSaturating(long)}
     * or {@link #absWithOverflowMask(long)} when callers need explicit overflow handling.
     *
     * @param value source value
     * @return JDK-style wrapped-width absolute value
     */
    /*@ public normal_behavior
      @   ensures \result == ( 0 <= value ? value : value == Long.MIN_VALUE ? Long.MIN_VALUE : -value);
      @*/
    public static /*@ pure @*/ long absJDK(final long value) {
        return Math.abs(value);
    }

    /**
     * Returns the branchless wrapped-width absolute value of {@code value}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It preserves the legacy
     * branchless kernel shape and does not signal the single overflow case where the mathematical
     * absolute value is unrepresentable in the same width. In current benchmarks it is slower than
     * {@link #absJDK(int)} for scalar {@code int} throughput.
     *
     * @param value source value
     * @return wrapped-width absolute value using the legacy bit kernel
     */
    /*@ public normal_behavior
      @   ensures \result == ( 0 <= value ? value : value == Integer.MIN_VALUE ? Integer.MIN_VALUE : -value);
      @*/
    public static /*@ pure @*/ int absUnsafe(final int value) {
        final int signMask = value >> 31;
        return (value + signMask) ^ signMask;
    }

    /**
     * Returns the branchless wrapped-width absolute value of {@code value}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It preserves the legacy
     * branchless kernel shape and does not signal the single overflow case where the mathematical
     * absolute value is unrepresentable in the same width. In current benchmarks it is effectively
     * tied with {@link #absJDK(long)} for scalar {@code long} throughput.
     *
     * @param value source value
     * @return wrapped-width absolute value using the legacy bit kernel
     */
    /*@ public normal_behavior
      @   ensures \result == ( 0 <= value ? value : value == Long.MIN_VALUE ? Long.MIN_VALUE : -value);
      @*/
    public static /*@ pure @*/ long absUnsafe(final long value) {
        final long signMask = value >> 63;
        return (value + signMask) ^ signMask;
    }

    /**
     * Returns the wrapped-width absolute value and a full-width overflow mask.
     *
     * <p>Postconditions: {@code overflowMask()} is {@code 0} when the mathematical absolute value
     * fits in {@code int}; otherwise it is {@code -1} and {@code value()} contains the wrapped JVM
     * result. In current benchmarks this stronger-semantics variant is materially slower than both
     * {@link #absJDK(int)} and {@link #absUnsafe(int)}.
     *
     * @param value source value
     * @return wrapped-width magnitude with an overflow mask suitable for branchless repair
     */
    /*@ public normal_behavior
      @   ensures \result != null;
      @*/
    @CheckReturnValue
    public static /*@ pure @*/ IntWithOverflowMask absWithOverflowMask(final int value) {
        return new IntWithOverflowMask(absUnsafe(value), equalMask(value, Integer.MIN_VALUE));
    }

    /**
     * Returns the wrapped-width absolute value and a full-width overflow mask.
     *
     * <p>Postconditions: {@code overflowMask()} is {@code 0} when the mathematical absolute value
     * fits in {@code long}; otherwise it is {@code -1} and {@code value()} contains the wrapped JVM
     * result. In current benchmarks this stronger-semantics variant is materially slower than both
     * {@link #absJDK(long)} and {@link #absUnsafe(long)}.
     *
     * @param value source value
     * @return wrapped-width magnitude with an overflow mask suitable for branchless repair
     */
    /*@ public normal_behavior
      @   ensures \result != null;
      @*/
    @CheckReturnValue
    public static /*@ pure @*/ LongWithOverflowMask absWithOverflowMask(final long value) {
        return new LongWithOverflowMask(absUnsafe(value), equalMask(value, Long.MIN_VALUE));
    }

    /**
     * Returns the saturating absolute value of {@code value}.
     *
     * <p>Safety requirements: this stronger-semantics variant is materially slower than
     * {@link #absJDK(int)} and {@link #absUnsafe(int)} in current benchmarks.
     *
     * @param value source value
     * @return {@code abs(value)} when representable; otherwise {@code Integer.MAX_VALUE}
     */
    /*@ public normal_behavior
      @   ensures \result == ( 0 <= value ? value : value == Integer.MIN_VALUE ? Integer.MAX_VALUE : -value);
      @*/
    /*@ skipesc @*/
    public static /*@ pure @*/ int absSaturating(final int value) {
        final IntWithOverflowMask result = absWithOverflowMask(value);
        return (result.value() & ~result.overflowMask()) | (Integer.MAX_VALUE & result.overflowMask());
    }

    /**
     * Returns the saturating absolute value of {@code value}.
     *
     * <p>Safety requirements: this stronger-semantics variant is materially slower than
     * {@link #absJDK(long)} and {@link #absUnsafe(long)} in current benchmarks.
     *
     * @param value source value
     * @return {@code abs(value)} when representable; otherwise {@code Long.MAX_VALUE}
     */
    /*@ public normal_behavior
      @   ensures \result == ( 0 <= value ? value : value == Long.MIN_VALUE ? Long.MAX_VALUE : -value);
      @*/
    /*@ skipesc @*/
    public static /*@ pure @*/ long absSaturating(final long value) {
        final LongWithOverflowMask result = absWithOverflowMask(value);
        return (result.value() & ~result.overflowMask()) | (Long.MAX_VALUE & result.overflowMask());
    }

    /**
     * Returns the JDK-style wrapped-width absolute difference of {@code left} and {@code right}.
     * The `abs` JDK code is written as {@code return (a < 0) ? -a : a;;} and is not branchless
     * by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant delegates to ordinary Java subtraction plus
     * {@link Math#abs(int)}. It preserves JVM wraparound semantics rather than the mathematical
     * absolute difference when the intermediate subtraction overflows. In current benchmarks it is
     * the fastest scalar {@code int} absolute-difference variant on this JVM. Use
     * {@link #absDiffSaturating(int, int)} or {@link #absDiffWithOverflowMask(int, int)} when
     * callers need explicit overflow handling.
     *
     * @param left first value
     * @param right second value
     * @return JDK-style wrapped-width absolute difference
     */
    /*@ public normal_behavior
      @   ensures \result == Math.abs(left - right);
      @*/
    public static /*@ pure @*/ int absDiffJDK(final int left, final int right) {
        return Math.abs(left - right);
    }

    /**
     * Returns the JDK-style wrapped-width absolute difference of {@code left} and {@code right}.
     * The `abs` JDK code is written as {@code return (a < 0) ? -a : a;;} and is not branchless
     * by default.
     * The intrinsics should replace that with a branchless variant, but use carefully.
     *
     * <p>Safety requirements: this variant delegates to ordinary Java subtraction plus
     * {@link Math#abs(long)}. It preserves JVM wraparound semantics rather than the mathematical
     * absolute difference when the intermediate subtraction overflows. In current benchmarks it is
     * the fastest scalar {@code long} absolute-difference variant on this JVM. Use
     * {@link #absDiffSaturating(long, long)} or {@link #absDiffWithOverflowMask(long, long)} when
     * callers need explicit overflow handling.
     *
     * @param left first value
     * @param right second value
     * @return JDK-style wrapped-width absolute difference
     */
    /*@ public normal_behavior
      @   ensures \result == Math.abs(left - right);
      @*/
    public static /*@ pure @*/ long absDiffJDK(final long left, final long right) {
        return Math.abs(left - right);
    }

    /**
     * Returns the branchless wrapped-width absolute difference of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It preserves the legacy
     * subtraction-and-sign-flip kernel and does not signal intermediate subtraction overflow. In
     * current benchmarks it is faster than the stronger-semantics variants but slower than
     * {@link #absDiffJDK(int, int)}.
     *
     * @param left first value
     * @param right second value
     * @return wrapped-width absolute difference using the legacy bit kernel
     */
    /*@ public normal_behavior
      @   ensures \result == Math.abs(left - right);
      @*/
    public static /*@ pure @*/ int absDiffUnsafe(final int left, final int right) {
        final int difference = left - right;
        final int signMask = difference >> 31;
        return (difference ^ signMask) - signMask;
    }

    /**
     * Returns the branchless wrapped-width absolute difference of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this variant is intentionally unsafe. It preserves the legacy
     * subtraction-and-sign-flip kernel and does not signal intermediate subtraction overflow. In
     * current benchmarks it is faster than the stronger-semantics variants but slower than
     * {@link #absDiffJDK(long, long)}.
     *
     * @param left first value
     * @param right second value
     * @return wrapped-width absolute difference using the legacy bit kernel
     */
    /*@ public normal_behavior
      @   ensures \result == Math.abs(left - right);
      @*/
    public static /*@ pure @*/ long absDiffUnsafe(final long left, final long right) {
        final long difference = left - right;
        final long signMask = difference >> 63;
        return (difference ^ signMask) - signMask;
    }

    /**
     * Returns the wrapped-width absolute difference and a full-width overflow mask.
     *
     * <p>Postconditions: {@code overflowMask()} is {@code 0} when {@code |left - right|} fits in
     * {@code int}; otherwise it is {@code -1} and {@code value()} contains the wrapped JVM result.
     * In current benchmarks this stronger-semantics variant is materially slower than
     * {@link #absDiffJDK(int, int)} and {@link #absDiffUnsafe(int, int)}.
     *
     * @param left first value
     * @param right second value
     * @return wrapped-width absolute difference with overflow signaling
     */
    /*@ public normal_behavior
      @   ensures \result != null;
      @*/
    @CheckReturnValue
    public static /*@ pure @*/ IntWithOverflowMask absDiffWithOverflowMask(final int left, final int right) {
        // Reorder first so the mathematical difference is non-negative. Any negative wrapped result
        // after subtraction therefore signals overflow and can be propagated as a full-width mask.
        final int larger = max(left, right);
        final int smaller = min(left, right);
        final int difference = larger - smaller;
        return new IntWithOverflowMask(difference, difference >> 31);
    }

    /**
     * Returns the wrapped-width absolute difference and a full-width overflow mask.
     *
     * <p>Postconditions: {@code overflowMask()} is {@code 0} when {@code |left - right|} fits in
     * {@code long}; otherwise it is {@code -1} and {@code value()} contains the wrapped JVM result.
     * In current benchmarks this stronger-semantics variant is materially slower than
     * {@link #absDiffJDK(long, long)} and {@link #absDiffUnsafe(long, long)}.
     *
     * @param left first value
     * @param right second value
     * @return wrapped-width absolute difference with overflow signaling
     */
    /*@ public normal_behavior
      @   ensures \result != null;
      @*/
    @CheckReturnValue
    public static /*@ pure @*/ LongWithOverflowMask absDiffWithOverflowMask(final long left, final long right) {
        final long larger = max(left, right);
        final long smaller = min(left, right);
        final long difference = larger - smaller;
        return new LongWithOverflowMask(difference, difference >> 63);
    }

    /**
     * Returns the saturating absolute difference of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this stronger-semantics variant is materially slower than
     * {@link #absDiffJDK(int, int)} and {@link #absDiffUnsafe(int, int)} in current benchmarks.
     *
     * @param left first value
     * @param right second value
     * @return {@code |left - right|} when representable; otherwise {@code Integer.MAX_VALUE}
     */
    /*@ public normal_behavior
      @   ensures \result >= 0;
      @*/
    /*@ skipesc @*/
    public static /*@ pure @*/ int absDiffSaturating(final int left, final int right) {
        final IntWithOverflowMask result = absDiffWithOverflowMask(left, right);
        return (result.value() & ~result.overflowMask()) | (Integer.MAX_VALUE & result.overflowMask());
    }

    /**
     * Returns the saturating absolute difference of {@code left} and {@code right}.
     *
     * <p>Safety requirements: this stronger-semantics variant is materially slower than
     * {@link #absDiffJDK(long, long)} and {@link #absDiffUnsafe(long, long)} in current benchmarks.
     *
     * @param left first value
     * @param right second value
     * @return {@code |left - right|} when representable; otherwise {@code Long.MAX_VALUE}
     */
    /*@ public normal_behavior
      @   ensures \result >= 0;
      @*/
    /*@ skipesc @*/
    public static /*@ pure @*/ long absDiffSaturating(final long left, final long right) {
        final LongWithOverflowMask result = absDiffWithOverflowMask(left, right);
        return (result.value() & ~result.overflowMask()) | (Long.MAX_VALUE & result.overflowMask());
    }

    /**
     * Returns an all-ones mask when {@code left < right}; otherwise returns zero.
     *
     * @param left first value
     * @param right second value
     * @return {@code -1} for true and {@code 0} for false
     */
    /*@ private normal_behavior
      @   ensures left < right ==> \result == -1;
      @   ensures left >= right ==> \result == 0;
      @*/
    private static /*@ pure @*/ int lessThanMask(final int left, final int right) {
        final int leftSignMask = left >> 31;
        final int rightSignMask = right >> 31;
        final int differentSignMask = leftSignMask ^ rightSignMask;
        final int subtractionSignMask = (left - right) >> 31;
        return (differentSignMask & leftSignMask) | (~differentSignMask & subtractionSignMask);
    }

    /**
     * Returns an all-ones mask when {@code left < right} using the legacy subtraction-only kernel.
     *
     * <p>Safety requirements: this helper is intentionally unsafe and only correct when
     * {@code left - right} cannot overflow.
     *
     * @param left first value
     * @param right second value
     * @return {@code -1} for true and {@code 0} for false when subtraction stays in range
     */
    /*@ private normal_behavior
      @   ensures \result == 0 || \result == -1;
      @*/
    private static /*@ pure @*/ int subtractLessThanMaskUnsafe(final int left, final int right) {
        return (left - right) >> 31;
    }

    /**
     * Returns an all-ones mask when {@code left < right}; otherwise returns zero.
     *
     * @param left first value
     * @param right second value
     * @return {@code -1} for true and {@code 0} for false
     */
    /*@ private normal_behavior
      @   ensures left < right ==> \result == -1L;
      @   ensures left >= right ==> \result == 0L;
      @*/
    private static /*@ pure @*/ long lessThanMask(final long left, final long right) {
        final long leftSignMask = left >> 63;
        final long rightSignMask = right >> 63;
        final long differentSignMask = leftSignMask ^ rightSignMask;
        final long subtractionSignMask = (left - right) >> 63;
        return (differentSignMask & leftSignMask) | (~differentSignMask & subtractionSignMask);
    }

    /**
     * Returns an all-ones mask when {@code left < right} using the legacy subtraction-only kernel.
     *
     * <p>Safety requirements: this helper is intentionally unsafe and only correct when
     * {@code left - right} cannot overflow.
     *
     * @param left first value
     * @param right second value
     * @return {@code -1} for true and {@code 0} for false when subtraction stays in range
     */
    /*@ private normal_behavior
      @   ensures \result == 0L || \result == -1L;
      @*/
    private static /*@ pure @*/ long subtractLessThanMaskUnsafe(final long left, final long right) {
        return (left - right) >> 63;
    }

    /**
     * Returns an all-ones mask when {@code left == right}; otherwise returns zero.
     *
     * @param left first value
     * @param right second value
     * @return {@code -1} for equality and {@code 0} otherwise
     */
    /*@ private normal_behavior
      @   ensures left == right ==> \result == -1;
      @   ensures left != right ==> \result == 0;
      @*/
    private static /*@ pure @*/ int equalMask(final int left, final int right) {
        return -equal(left, right);
    }

    /**
     * Returns an all-ones mask when {@code left == right}; otherwise returns zero.
     *
     * @param left first value
     * @param right second value
     * @return {@code -1} for equality and {@code 0} otherwise
     */
    /*@ private normal_behavior
      @   ensures left == right ==> \result == -1L;
      @   ensures left != right ==> \result == 0L;
      @*/
    private static /*@ pure @*/ long equalMask(final long left, final long right) {
        return -equal(left, right);
    }
}
