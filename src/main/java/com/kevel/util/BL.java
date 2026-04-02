package com.kevel.util;

/**
 * The BL (Branchless) class provides static methods for common comparison and math operations.
 *
 * <p>If the methods are given `long` arguments, they will return a `long`.
 * <p>If the methods are given `int` arguments, they will return an `int`.
 *
 * <p>For comparison methods between an `a` or `b`, the return will be `a` or `b`.
 * For most predicate functions, the return will be a `1` for true, or a `0` for false.
 * Eg: You can use these return with multiplication / addition, to remove conditionals in your logic.
 *
 * <p>Some predicate methods within Java Math lib _should_ be branchless (via an Intrinsic hint),
 * but that may not happen. This library enables you to ensure they are always branchless,
 * and the code does not tag methods with `@IntrinsicCandidate`.
 * Please measure your specific usecase.
 *
 * <p>These branchless methods/functions can be useful for vectorized operations.
 */
public final class BL {

    private BL() {}

    // Predicates
    // -------------------

    /**
     * Return 1 if a and b are equal, else 0.
     */
    public static long equal(final long a, final long b) {
        return (((a ^ b) | -(a ^ b)) >> 63 & 1) ^ 1;
    }

    /**
     * Return 1 if a and b are equal, else 0.
     */
    public static int equal(final int a, final int b) {
        return (((a ^ b) | -(a ^ b)) >> 31 & 1) ^ 1;
    }

    /**
     * Return 1 if a and b are not equal, else 0.
     */
    public static long notEqual(final long a, final long b) {
        return (((a ^ b) | -(a ^ b)) >> 63 & 1);
    }

    /**
     * Return 1 if a and b are not equal, else 0.
     */
    public static int notEqual(final int a, final int b) {
        return (((a ^ b) | -(a ^ b)) >> 31 & 1);
    }

    /**
     * Return 1 if {@code a < b}, else 0.
     */
    public static long lt(final long a, final long b) {
        return ((a - b) >> 63) & 1;
    }

    /**
     * Return 1 if {@code a < b}, else 0.
     */
    public static int lt(final int a, final int b) {
        return ((a - b) >> 31) & 1;
    }

    /**
     * Return 1 if {@code a > b}, else 0.
     */
    public static long gt(final long a, final long b) {
        return ((b - a) >> 63) & 1;
    }

    /**
     * Return 1 if {@code a > b}, else 0.
     */
    public static int gt(final int a, final int b) {
        return ((b - a) >> 31) & 1;
    }

    /**
     * Return 1 if {@code a <= b}, else 0.
     */
    public static long lte(final long a, final long b) {
        return ((b - a) >> 63 & 1) ^ 1;
    }

    /**
     * Return 1 if {@code a <= b}, else 0.
     */
    public static int lte(final int a, final int b) {
        return ((b - a) >> 31 & 1) ^ 1;
    }

    /**
     * Return 1 if {@code a >= b}, else 0.
     */
    public static long gte(final long a, final long b) {
        return ((a - b) >> 63 & 1) ^ 1;
    }

    /**
     * Return 1 if {@code a >= b}, else 0.
     */
    public static int gte(final int a, final int b) {
        return ((a - b) >> 31 & 1) ^ 1;
    }

    /**
     * Return 1 if a is 0, else 0.
     */
    public static long isZero(final long a) {
        return (((a | -a) >> 63) & 1) ^ 1;
    }

    /**
     * Return 1 if a is 0, else 0.
     */
    public static int isZero(final int a) {
        return (((a | -a) >> 31) & 1) ^ 1;
    }

    /**
     * Return 1 if a is not 0, else 0.
     */
    public static long isNotZero(final long a) {
        return (((a | -a) >> 63) & 1);
    }

    /**
     * Return 1 if a is not 0, else 0.
     */
    public static int isNotZero(final int a) {
        return (((a | -a) >> 31) & 1);
    }

    /**
     * Return 1 if {@code a > 0}, else 0.
     */
    public static long isPositive(final long a) {
        return (-a >> 63) & 1;
    }

    /**
     * Return 1 if {@code a > 0}, else 0.
     */
    public static int isPositive(final int a) {
        return (-a >> 31) & 1;
    }

    /**
     * Return 1 if {@code a < 0}, else 0.
     */
    public static long isNegative(final long a) {
        return (a >> 63) & 1;
    }

    /**
     * Return 1 if {@code a < 0}, else 0.
     */
    public static int isNegative(final int a) {
        return (a >> 31) & 1;
    }

    /**
     * Return 1 if a is even, else 0.
     */
    public static long isEven(final long a) {
        return (a & 1) ^ 1;
    }

    /**
     * Return 1 if a is even, else 0.
     */
    public static int isEven(final int a) {
        return (a & 1) ^ 1;
    }

    /**
     * Return 1 if a is odd, else 0.
     */
    public static long isOdd(final long a) {
        return a & 1;
    }

    /**
     * Return 1 if a is odd, else 0.
     */
    public static int isOdd(final int a) {
        return a & 1;
    }

    /**
     * Return 1 if a is a power of two or is zero, else 0.
     */
    public static long isPowerOfTwo(final long a) {
        return (~a & (a - 1)) & 1;
    }

    /**
     * Return 1 if a is a power of two or is zero, else 0.
     */
    public static int isPowerOfTwo(final int a) {
        return (~a & (a - 1)) & 1;
    }

    /**
     * Return 1 if a is divisible by b, else 0.
     */
    public static long isDivisibleBy(final long a, final long b) {
        return (a % b & 1) ^ 1;
    }

    /**
     * Return 1 if a is divisible by b, else 0.
     */
    public static int isDivisibleBy(final int a, final int b) {
        return (a % b & 1) ^ 1;
    }

    // Strange predicates
    // -------------------

    /**
     * Return 1 if {@code a >= 0}, else -1.
     */
    public static long isNatural(final long a) {
        return ((a >> 63) | 1);
    }

    /**
     * Return 1 if {@code a >= 0}, else -1.
     */
    public static int isNatural(final int a) {
        return ((a >> 31) | 1);
    }

    // Select operations
    // -------------------

    /**
     * If a == 1, return x, else y.
     */
    public static long select(final long a, final long x, final long y) {
        /*
        long mask = (equal(a,1) * -1);
        return ((x ^ y) & mask) ^ y;
        */
        return equalRetX(a, 1, x, y);
    }

    /**
     * If a == 1, return x, else y.
     */
    public static int select(final int a, final int x, final int y) {
        /*
        int mask = (equal(a,1) * -1);
        return ((x ^ y) & mask) ^ y;
        */
        return equalRetX(a, 1, x, y);
    }

    /**
     * If a==b, return x, else y.
     */
    public static long equalRetX(final long a, final long b, final long x, final long y) {
        long r = ((a - b) - 1) >> 63;
        long mask = (((a - b) >> 63) ^ r) & r;
        return (x & mask) | (y & (~mask));
    }

    /**
     * If a==b, return x, else y.
     */
    public static int equalRetX(final int a, final int b, final int x, final int y) {
        int r = ((a - b) - 1) >> 31;
        int mask = (((a - b) >> 31) ^ r) & r;
        return (x & mask) | (y & (~mask));
    }

    // Math operations
    // -------------------

    /**
     * Return the maximum of a and b.
     */
    public static long max(final long a, final long b) {
        return a ^ ((a ^ b) & ((a - b) >> 63));
    }

    /**
     * Return the maximum of a and b.
     */
    public static int max(final int a, final int b) {
        return a ^ ((a ^ b) & ((a - b) >> 31));
    }

    /**
     * Return the min of a and b.
     */
    public static long min(final long a, final long b) {
        return b ^ ((a ^ b) & ((a - b) >> 63));
    }

    /**
     * Return the min of a and b.
     */
    public static int min(final int a, final int b) {
        return b ^ ((a ^ b) & ((a - b) >> 31));
    }

    /**
     * Clamp `a` between a min and max, inclusive.
     */
    public static long clamp(final long a, final long min, final long max) {
        return min(max(a, min), max);
    }

    /**
     * Clamp `a` between a min and max, inclusive.
     */
    public static int clamp(final int a, final int min, final int max) {
        return min(max(a, min), max);
    }

    /**
     * Return the absolute value of a.
     */
    public static long abs(final long a) {
        return (a + (a >> 63)) ^ (a >> 63);
    }

    /**
     * Return the absolute value of a.
     */
    public static int abs(final int a) {
        return (a + (a >> 31)) ^ (a >> 31);
    }

    /**
     * Return the absolute difference between a and b.
     */
    public static long absDiff(final long a, final long b) {
        return ((a - b) ^ ((a - b) >> 63)) - ((a - b) >> 63);
    }

    /**
     * Return the absolute difference between a and b.
     */
    public static int absDiff(final int a, final int b) {
        return ((a - b) ^ ((a - b) >> 31)) - ((a - b) >> 31);
    }
}
