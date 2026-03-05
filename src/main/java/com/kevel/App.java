package com.kevel;

public final class App {
    private App() {}

    public static void main(String[] args) {
        System.out.println("10 and 20 make: " + increasingSum(10, 20));
        System.out.println("20 and 10 make: " + increasingSum(20, 10) + " <-- look at that!");
    }

    /** Given two positive numbers in increasing order,
     *  return their sum if they're divisible,
     *  otherwise return y+1.
     *
     * @ requires x > 0
     * @ requires y >= x
     * @ ensures /ret > y
     * @ pure
     */
    public static int increasingSum(final int x, final int y) {
        assert x <= y : "Args were not in increasing order: " + x + " and " + y;

        // Notice that this code looks "correct" but suffers from overflow errors
        // and if assertions are turned off, can produce a divide-by-zero error.
        // Our JML specs can catch all of that

        return y % x == 0 ? x + y : y + 1;
    }
}
