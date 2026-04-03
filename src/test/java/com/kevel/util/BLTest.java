package com.kevel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BLTest {

    @Test
    void orderingPredicatesHandleSignedBoundaryValues() {
        assertEquals(0, BL.lt(Integer.MAX_VALUE, -1));
        assertEquals(1, BL.lt(Integer.MIN_VALUE, 1));
        assertEquals(1, BL.gt(Integer.MAX_VALUE, -1));
        assertEquals(0, BL.gt(Integer.MIN_VALUE, 1));

        assertEquals(0L, BL.lt(Long.MAX_VALUE, -1L));
        assertEquals(1L, BL.lt(Long.MIN_VALUE, 1L));
        assertEquals(1L, BL.gt(Long.MAX_VALUE, -1L));
        assertEquals(0L, BL.gt(Long.MIN_VALUE, 1L));
    }

    @Test
    void powerOfTwoPredicatesRejectNegativeAndCompositeValues() {
        assertEquals(1, BL.isPowerOfTwoOrZero(0));
        assertEquals(1, BL.isPowerOfTwo(8));
        assertEquals(0, BL.isPowerOfTwo(12));
        assertEquals(0, BL.isPowerOfTwo(-8));

        assertEquals(1L, BL.isPowerOfTwoOrZero(0L));
        assertEquals(1L, BL.isPowerOfTwo(8L));
        assertEquals(0L, BL.isPowerOfTwo(12L));
        assertEquals(0L, BL.isPowerOfTwo(-8L));
    }

    @Test
    void divisibilityHandlesZeroDivisorsAndRemainders() {
        assertEquals(0, BL.isDivisibleBy(10, 0));
        assertEquals(1, BL.isDivisibleBy(10, 5));
        assertEquals(0, BL.isDivisibleBy(6, 4));

        assertEquals(0L, BL.isDivisibleBy(10L, 0L));
        assertEquals(1L, BL.isDivisibleBy(10L, 5L));
        assertEquals(0L, BL.isDivisibleBy(6L, 4L));
    }

    @Test
    void selectAndEqualRetXUseOneBitPredicates() {
        assertEquals(7, BL.select(1, 7, 11));
        assertEquals(11, BL.select(0, 7, 11));
        assertEquals(7, BL.equalRetX(4, 4, 7, 11));
        assertEquals(11, BL.equalRetX(4, 5, 7, 11));

        assertEquals(7L, BL.select(1L, 7L, 11L));
        assertEquals(11L, BL.select(0L, 7L, 11L));
        assertEquals(7L, BL.equalRetX(4L, 4L, 7L, 11L));
        assertEquals(11L, BL.equalRetX(4L, 5L, 7L, 11L));
    }

    @Test
    void maxMinAndClampRespectSignedOrdering() {
        assertEquals(Integer.MAX_VALUE, BL.max(Integer.MAX_VALUE, -1));
        assertEquals(-1, BL.min(Integer.MAX_VALUE, -1));
        assertEquals(10, BL.clamp(12, 0, 10));
        assertEquals(0, BL.clamp(-5, 0, 10));
        assertEquals(7, BL.clamp(7, 0, 10));

        assertEquals(Long.MAX_VALUE, BL.max(Long.MAX_VALUE, -1L));
        assertEquals(-1L, BL.min(Long.MAX_VALUE, -1L));
        assertEquals(10L, BL.clamp(12L, 0L, 10L));
        assertEquals(0L, BL.clamp(-5L, 0L, 10L));
        assertEquals(7L, BL.clamp(7L, 0L, 10L));
    }

    @Test
    void jdkVariantsRespectSignedSemantics() {
        assertEquals(Integer.MAX_VALUE, BL.maxJDK(Integer.MAX_VALUE, -1));
        assertEquals(-1, BL.minJDK(Integer.MAX_VALUE, -1));
        assertEquals(10, BL.clampJDK(12, 0, 10));
        assertEquals(7, BL.absJDK(-7));
        assertEquals(Integer.MIN_VALUE, BL.absJDK(Integer.MIN_VALUE));
        assertEquals(9, BL.absDiffJDK(12, 3));
        assertEquals(1, BL.absDiffJDK(Integer.MIN_VALUE, Integer.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, BL.maxJDK(Long.MAX_VALUE, -1L));
        assertEquals(-1L, BL.minJDK(Long.MAX_VALUE, -1L));
        assertEquals(10L, BL.clampJDK(12L, 0L, 10L));
        assertEquals(7L, BL.absJDK(-7L));
        assertEquals(Long.MIN_VALUE, BL.absJDK(Long.MIN_VALUE));
        assertEquals(9L, BL.absDiffJDK(12L, 3L));
        assertEquals(1L, BL.absDiffJDK(Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    void unsafeVariantsMatchLegacySemanticsWhenInputsAreBounded() {
        assertEquals(1, BL.ltUnsafe(-5, 7));
        assertEquals(0, BL.ltUnsafe(7, -5));
        assertEquals(1, BL.gtUnsafe(7, -5));
        assertEquals(1, BL.lteUnsafe(-5, 7));
        assertEquals(1, BL.gteUnsafe(7, -5));
        assertEquals(7, BL.maxUnsafe(-5, 7));
        assertEquals(-5, BL.minUnsafe(-5, 7));
        assertEquals(10, BL.clampUnsafe(12, 0, 10));
        assertEquals(1, BL.isPositiveUnsafe(7));
        assertEquals(0, BL.isPositiveUnsafe(-7));
        assertEquals(7, BL.absUnsafe(-7));
        assertEquals(12, BL.absDiffUnsafe(-5, 7));

        assertEquals(1L, BL.ltUnsafe(-5L, 7L));
        assertEquals(0L, BL.ltUnsafe(7L, -5L));
        assertEquals(1L, BL.gtUnsafe(7L, -5L));
        assertEquals(1L, BL.lteUnsafe(-5L, 7L));
        assertEquals(1L, BL.gteUnsafe(7L, -5L));
        assertEquals(7L, BL.maxUnsafe(-5L, 7L));
        assertEquals(-5L, BL.minUnsafe(-5L, 7L));
        assertEquals(10L, BL.clampUnsafe(12L, 0L, 10L));
        assertEquals(1L, BL.isPositiveUnsafe(7L));
        assertEquals(0L, BL.isPositiveUnsafe(-7L));
        assertEquals(7L, BL.absUnsafe(-7L));
        assertEquals(12L, BL.absDiffUnsafe(-5L, 7L));
    }

    @Test
    void unsafeVariantsExposeLegacyOverflowBehavior() {
        assertEquals(1, BL.isPositiveUnsafe(Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE, BL.absUnsafe(Integer.MIN_VALUE));
        assertEquals(1, BL.absDiffUnsafe(Integer.MIN_VALUE, Integer.MAX_VALUE));

        assertEquals(1L, BL.isPositiveUnsafe(Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE, BL.absUnsafe(Long.MIN_VALUE));
        assertEquals(1L, BL.absDiffUnsafe(Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Test
    void absWithOverflowMaskSignalsMinValueOverflow() {
        BL.IntWithOverflowMask intResult = BL.absWithOverflowMask(Integer.MIN_VALUE);
        BL.LongWithOverflowMask longResult = BL.absWithOverflowMask(Long.MIN_VALUE);

        assertEquals(Integer.MIN_VALUE, intResult.value());
        assertEquals(-1, intResult.overflowMask());
        assertEquals(Integer.MAX_VALUE, BL.absSaturating(Integer.MIN_VALUE));

        assertEquals(Long.MIN_VALUE, longResult.value());
        assertEquals(-1L, longResult.overflowMask());
        assertEquals(Long.MAX_VALUE, BL.absSaturating(Long.MIN_VALUE));
    }

    @Test
    void absDiffWithOverflowMaskSignalsWidthOverflow() {
        BL.IntWithOverflowMask intResult = BL.absDiffWithOverflowMask(Integer.MIN_VALUE, Integer.MAX_VALUE);
        BL.LongWithOverflowMask longResult = BL.absDiffWithOverflowMask(Long.MIN_VALUE, Long.MAX_VALUE);

        assertTrue(intResult.overflowMask() < 0);
        assertEquals(Integer.MAX_VALUE, BL.absDiffSaturating(Integer.MIN_VALUE, Integer.MAX_VALUE));

        assertTrue(longResult.overflowMask() < 0L);
        assertEquals(Long.MAX_VALUE, BL.absDiffSaturating(Long.MIN_VALUE, Long.MAX_VALUE));
    }

    @Property
    void intPredicatesMatchJavaOperators(@ForAll int left, @ForAll int right) {
        assertEquals(left == right ? 1 : 0, BL.equal(left, right));
        assertEquals(left != right ? 1 : 0, BL.notEqual(left, right));
        assertEquals(left < right ? 1 : 0, BL.lt(left, right));
        assertEquals(left > right ? 1 : 0, BL.gt(left, right));
        assertEquals(left <= right ? 1 : 0, BL.lte(left, right));
        assertEquals(left >= right ? 1 : 0, BL.gte(left, right));
        assertEquals(Math.max(left, right), BL.max(left, right));
        assertEquals(Math.max(left, right), BL.maxJDK(left, right));
        assertEquals(Math.min(left, right), BL.min(left, right));
        assertEquals(Math.min(left, right), BL.minJDK(left, right));
    }

    @Property
    void longPredicatesMatchJavaOperators(@ForAll long left, @ForAll long right) {
        assertEquals(left == right ? 1L : 0L, BL.equal(left, right));
        assertEquals(left != right ? 1L : 0L, BL.notEqual(left, right));
        assertEquals(left < right ? 1L : 0L, BL.lt(left, right));
        assertEquals(left > right ? 1L : 0L, BL.gt(left, right));
        assertEquals(left <= right ? 1L : 0L, BL.lte(left, right));
        assertEquals(left >= right ? 1L : 0L, BL.gte(left, right));
        assertEquals(Math.max(left, right), BL.max(left, right));
        assertEquals(Math.max(left, right), BL.maxJDK(left, right));
        assertEquals(Math.min(left, right), BL.min(left, right));
        assertEquals(Math.min(left, right), BL.minJDK(left, right));
    }

    @Property
    void unsafeVariantsMatchJavaWithinBoundedRanges(
            @ForAll("boundedInts") int left,
            @ForAll("boundedInts") int right,
            @ForAll("boundedLongs") long longLeft,
            @ForAll("boundedLongs") long longRight) {
        assertEquals(left < right ? 1 : 0, BL.ltUnsafe(left, right));
        assertEquals(left > right ? 1 : 0, BL.gtUnsafe(left, right));
        assertEquals(left <= right ? 1 : 0, BL.lteUnsafe(left, right));
        assertEquals(left >= right ? 1 : 0, BL.gteUnsafe(left, right));
        assertEquals(Math.max(left, right), BL.maxUnsafe(left, right));
        assertEquals(Math.min(left, right), BL.minUnsafe(left, right));
        assertEquals(left > 0 ? 1 : 0, BL.isPositiveUnsafe(left));
        assertEquals(Math.abs(left), BL.absUnsafe(left));
        assertEquals(Math.abs(left - right), BL.absDiffUnsafe(left, right));

        int lower = Math.min(left, right);
        int upper = Math.max(left, right);
        assertEquals(Math.min(Math.max(0, lower), upper), BL.clampUnsafe(0, lower, upper));

        assertEquals(longLeft < longRight ? 1L : 0L, BL.ltUnsafe(longLeft, longRight));
        assertEquals(longLeft > longRight ? 1L : 0L, BL.gtUnsafe(longLeft, longRight));
        assertEquals(longLeft <= longRight ? 1L : 0L, BL.lteUnsafe(longLeft, longRight));
        assertEquals(longLeft >= longRight ? 1L : 0L, BL.gteUnsafe(longLeft, longRight));
        assertEquals(Math.max(longLeft, longRight), BL.maxUnsafe(longLeft, longRight));
        assertEquals(Math.min(longLeft, longRight), BL.minUnsafe(longLeft, longRight));
        assertEquals(longLeft > 0L ? 1L : 0L, BL.isPositiveUnsafe(longLeft));
        assertEquals(Math.abs(longLeft), BL.absUnsafe(longLeft));
        assertEquals(Math.abs(longLeft - longRight), BL.absDiffUnsafe(longLeft, longRight));

        long longLower = Math.min(longLeft, longRight);
        long longUpper = Math.max(longLeft, longRight);
        assertEquals(Math.min(Math.max(0L, longLower), longUpper), BL.clampUnsafe(0L, longLower, longUpper));
    }

    @Property
    void zeroSignAndParityPredicatesMatchJavaSemantics(@ForAll int intValue, @ForAll long longValue) {
        assertEquals(intValue == 0 ? 1 : 0, BL.isZero(intValue));
        assertEquals(intValue != 0 ? 1 : 0, BL.isNotZero(intValue));
        assertEquals(intValue > 0 ? 1 : 0, BL.isPositive(intValue));
        assertEquals(intValue < 0 ? 1 : 0, BL.isNegative(intValue));
        assertEquals(intValue >= 0 ? 1 : 0, BL.isNonNegative(intValue));
        assertEquals((intValue & 1) == 0 ? 1 : 0, BL.isEven(intValue));
        assertEquals((intValue & 1) != 0 ? 1 : 0, BL.isOdd(intValue));

        assertEquals(longValue == 0L ? 1L : 0L, BL.isZero(longValue));
        assertEquals(longValue != 0L ? 1L : 0L, BL.isNotZero(longValue));
        assertEquals(longValue > 0L ? 1L : 0L, BL.isPositive(longValue));
        assertEquals(longValue < 0L ? 1L : 0L, BL.isNegative(longValue));
        assertEquals(longValue >= 0L ? 1L : 0L, BL.isNonNegative(longValue));
        assertEquals((longValue & 1L) == 0L ? 1L : 0L, BL.isEven(longValue));
        assertEquals((longValue & 1L) != 0L ? 1L : 0L, BL.isOdd(longValue));
    }

    @Property
    void strictPowerOfTwoPredicatesMatchJavaReference(@ForAll int intValue, @ForAll long longValue) {
        assertEquals(isStrictPowerOfTwo(intValue) ? 1 : 0, BL.isPowerOfTwo(intValue));
        assertEquals(isPowerOfTwoOrZero(intValue) ? 1 : 0, BL.isPowerOfTwoOrZero(intValue));

        assertEquals(isStrictPowerOfTwo(longValue) ? 1L : 0L, BL.isPowerOfTwo(longValue));
        assertEquals(isPowerOfTwoOrZero(longValue) ? 1L : 0L, BL.isPowerOfTwoOrZero(longValue));
    }

    @Property
    void divisibilityMatchesJavaForNonZeroDivisors(
            @ForAll int dividend,
            @ForAll("nonZeroInts") int divisor,
            @ForAll long longDividend,
            @ForAll("nonZeroLongs") long longDivisor) {
        assertEquals(dividend % divisor == 0 ? 1 : 0, BL.isDivisibleBy(dividend, divisor));
        assertEquals(longDividend % longDivisor == 0L ? 1L : 0L, BL.isDivisibleBy(longDividend, longDivisor));
    }

    @Property
    void absMatchesJavaWhenMagnitudeFits(
            @ForAll("intsExceptMin") int intValue, @ForAll("longsExceptMin") long longValue) {
        BL.IntWithOverflowMask intResult = BL.absWithOverflowMask(intValue);
        BL.LongWithOverflowMask longResult = BL.absWithOverflowMask(longValue);

        assertEquals(0, intResult.overflowMask());
        assertEquals(Math.abs(intValue), intResult.value());
        assertEquals(Math.abs(intValue), BL.absJDK(intValue));
        assertEquals(Math.abs(intValue), BL.absUnsafe(intValue));
        assertEquals(Math.abs(intValue), BL.absSaturating(intValue));

        assertEquals(0L, longResult.overflowMask());
        assertEquals(Math.abs(longValue), longResult.value());
        assertEquals(Math.abs(longValue), BL.absJDK(longValue));
        assertEquals(Math.abs(longValue), BL.absUnsafe(longValue));
        assertEquals(Math.abs(longValue), BL.absSaturating(longValue));
    }

    @Property
    void absDiffMatchesExactReferenceWhenDifferenceFits(
            @ForAll int left, @ForAll int right, @ForAll long longLeft, @ForAll long longRight) {
        long intReference = Math.abs((long) left - right);
        BL.IntWithOverflowMask intResult = BL.absDiffWithOverflowMask(left, right);

        if (intReference <= Integer.MAX_VALUE) {
            assertEquals(0, intResult.overflowMask());
            assertEquals((int) intReference, intResult.value());
            assertEquals(Math.abs(left - right), BL.absDiffJDK(left, right));
            assertEquals(Math.abs(left - right), BL.absDiffUnsafe(left, right));
            assertEquals((int) intReference, BL.absDiffSaturating(left, right));
        } else {
            assertEquals(-1, intResult.overflowMask());
            assertEquals(Integer.MAX_VALUE, BL.absDiffSaturating(left, right));
        }

        long longReference = saturatingAbsDiffReference(longLeft, longRight);
        BL.LongWithOverflowMask longResult = BL.absDiffWithOverflowMask(longLeft, longRight);
        if (longReference != Long.MAX_VALUE || equalToLongMaxDistance(longLeft, longRight)) {
            assertEquals(Math.abs(longLeft - longRight), BL.absDiffJDK(longLeft, longRight));
            assertEquals(Math.abs(longLeft - longRight), BL.absDiffUnsafe(longLeft, longRight));
            assertEquals(longReference, BL.absDiffSaturating(longLeft, longRight));
        }
        if (hasLongAbsDiffOverflow(longLeft, longRight)) {
            assertEquals(-1L, longResult.overflowMask());
            assertEquals(Long.MAX_VALUE, BL.absDiffSaturating(longLeft, longRight));
        } else {
            assertEquals(0L, longResult.overflowMask());
            assertEquals(longReference, longResult.value());
        }
    }

    @Provide
    Arbitrary<Integer> nonZeroInts() {
        return Arbitraries.integers().filter(value -> value != 0);
    }

    @Provide
    Arbitrary<Long> nonZeroLongs() {
        return Arbitraries.longs().filter(value -> value != 0L);
    }

    @Provide
    Arbitrary<Integer> intsExceptMin() {
        return Arbitraries.integers().filter(value -> value != Integer.MIN_VALUE);
    }

    @Provide
    Arbitrary<Long> longsExceptMin() {
        return Arbitraries.longs().filter(value -> value != Long.MIN_VALUE);
    }

    @Provide
    Arbitrary<Integer> boundedInts() {
        return Arbitraries.integers().between(-1_000_000, 1_000_000);
    }

    @Provide
    Arbitrary<Long> boundedLongs() {
        return Arbitraries.longs().between(-1_000_000_000_000L, 1_000_000_000_000L);
    }

    private static boolean isStrictPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private static boolean isPowerOfTwoOrZero(int value) {
        return value >= 0 && (value & (value - 1)) == 0;
    }

    private static boolean isStrictPowerOfTwo(long value) {
        return value > 0L && (value & (value - 1L)) == 0L;
    }

    private static boolean isPowerOfTwoOrZero(long value) {
        return value >= 0L && (value & (value - 1L)) == 0L;
    }

    private static long saturatingAbsDiffReference(long left, long right) {
        if (hasLongAbsDiffOverflow(left, right)) {
            return Long.MAX_VALUE;
        }
        return Math.abs(left - right);
    }

    private static boolean hasLongAbsDiffOverflow(long left, long right) {
        if (left >= right) {
            return left - right < 0L;
        }
        return right - left < 0L;
    }

    private static boolean equalToLongMaxDistance(long left, long right) {
        return !hasLongAbsDiffOverflow(left, right) && Math.abs(left - right) == Long.MAX_VALUE;
    }
}
