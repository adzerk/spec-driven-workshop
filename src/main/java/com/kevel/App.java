package com.kevel;

public final class App {
    private App() {}

    // Even code without JML specifications are analyzed and checked by OpenJML.
    // We can explicitly disable "Extended Static Checking" (esc) and
    // Runtime Assertion Checking (rac) for methods we want OpenJML to skip.

    // //@ skipesc skiprac // this is commented out so we can see the spec error
    public static void main(String[] args) {
        System.out.println("10 and 20 make: " + increasingSum(10, 20));
        System.out.println("20 and 10 make: " + increasingSum(20, 10) + " <-- look at that!");
    }

    /** Given two positive numbers in increasing order,
     *  return their sum if they're divisible,
     *  otherwise return y+1.
     */
    /*@
        requires x > 0;
        requires y >= x;
        ensures \result > y;
        pure
        //code_java_math // Uncomment to silence overflow/underflow
    @*/
    public static int increasingSum(final int x, final int y) {
        assert x <= y : "Args were not in increasing order: " + x + " and " + y;

        // Notice that this code looks "correct" but suffers from overflow errors
        // and if assertions are turned off, can produce a divide-by-zero error.
        // Our JML specs can catch all of that

        return y % x == 0 ? x + y : y + 1;
    }
}
