package com.kevel;

public final class App {
    private App() {}

    public static void main(String[] args) {
        System.out.println(greet("world"));
        System.out.println(greet(null));
    }

    /** Return a friendly string
     *
     * @ requires name != null
     */
    static String greet(String name) {
        return "Hello, " + name + "!";
    }
}
