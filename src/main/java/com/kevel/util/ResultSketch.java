package com.kevel.util;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/*
 * Results are Values returned from functions,
 * enabling us to handle errors as Values/data.
 *
 * From Real World OCaml, 2e
 * Chapter 7: Error Handling
 *
 * "Choosing an Error-Handling Strategy"
 * https://dev.realworldocaml.org/error-handling.html#choosing-an-error-handling-strategy
 *
 * If you’re writing a rough-and-ready program where getting it done quickly
 * is key and failure is not that expensive, then using exceptions extensively
 * may be the way to go. If, on the other hand, you’re writing production
 * software whose failure is costly, then you should probably lean in the
 * direction of using error-aware return types.
 *
 * To be clear, it doesn’t make sense to avoid exceptions entirely.
 * The maxim of “use exceptions for exceptional conditions” applies.
 * If an error occurs sufficiently rarely, then throwing an exception
 * is often the right behavior.
 *
 * Also, for errors that are omnipresent, error-aware return types may be overkill.
 * A good example is out-of-memory errors, which can occur anywhere, and so
 * you’d need to use error-aware return types everywhere to capture those.
 * Having every operation marked as one that might fail is no more explicit
 * than having none of them marked.
 *
 * In short, for errors that are a foreseeable and ordinary part of the
 * execution of your production code and that are not omnipresent,
 * error-aware return types are typically the right solution.
 */

/*
 * Note:
 * For this Result type, Err can also carry data/context, in the same way
 * that Clojure's `ex-data` carries data.
 */

public sealed interface ResultSketch<T, E extends Throwable> extends Supplier<T> {

    public T value();

    default T get() {
        return switch (this) {
            case Ok<T, E> r -> r.value();
            case Err<T, E> e -> throw new RuntimeException(e.ex());
        };
    }

    // default <E extends Throwable> T getChecked() throws E {
    default T getChecked() throws E {
        return switch (this) {
            case Ok<T, E> r -> r.value(); // The only other result is Ok
            case Err<T, E> e -> ResultSketch.throwErr(e);
        };
    }

    default <U> U either(Function<Ok<T, E>, U> oFn, Function<Err<T, E>, U> eFn) {
        return switch (this) {
            case Ok<T, E> o -> oFn.apply(o);
            case Err<T, E> e -> eFn.apply(e);
        };
    }

    // This might be a bad idea.  Maybe this would be more predictable as Stream.of(this.get())
    default Stream<?> stream() {
        var t = this.get();
        if (t instanceof Collection<?> c) {
            return c.stream();
        }
        return Stream.of(t);
    }

    static <T, E extends Throwable, U, EE extends Throwable> ResultSketch<U, EE> pipe(
            ResultSketch<T, E> res, Function<T, ResultSketch<U, EE>> f1) {
        return f1.apply(res.get());
    }

    static <T, E extends Throwable, U, Uu, EE extends Throwable> ResultSketch<U, EE> pipe(
            ResultSketch<T, E> res, Function<T, ResultSketch<Uu, ?>> f1, Function<Uu, ResultSketch<U, EE>> f2) {
        return f2.apply(f1.apply(res.get()).get());
    }

    static <T, E extends Throwable, U, Uu, Uuu, EE extends Throwable> ResultSketch<U, EE> pipe(
            ResultSketch<T, E> res,
            Function<T, ResultSketch<Uu, ?>> f1,
            Function<Uu, ResultSketch<Uuu, ?>> f2,
            Function<Uuu, ResultSketch<U, EE>> f3) {
        return f3.apply(f2.apply(f1.apply(res.get()).get()).get());
    }

    record Ok<U, E extends Throwable>(U value) implements ResultSketch<U, E> {}

    record Err<U, E extends Throwable>(U value, E ex) implements ResultSketch<U, E> {}

    public static final Exception exception = new Exception("Error Result");

    public static <U, E extends Throwable> Ok<U, E> ok(U value) {
        return new Ok<>(value);
    }

    public static <U> Err<U, Exception> err(U value) {
        return new Err<>(value, exception);
    }

    public static <U, E extends Throwable> Err<U, E> err(U value, E ex) {
        return new Err<>(value, ex);
    }

    public static <U, E extends Throwable> U throwErr(Err<U, E> e) throws E {
        throw e.ex();
    }

    public static void example() {
        var res1 = ok(1);
        var res2 = ResultSketch.pipe(res1, (x) -> ok(x + 1), (y) -> ok(y.toString()));
        assert res2.get() == "2";

        System.out.println("res is: " + res2);

        var res3 = ok(java.util.List.of(1, 2, 3, 4));
        System.out.println("Even list: "
                + res3.stream() // `stream` sees through the Collection values within Results
                        .filter((x) -> (int) x % 2 == 0)
                        .toList());
        System.out.println("Single value stream: " + res2.stream().toList());

        var res4 = err(4); // Errors carry data and optionally a detailed exception

        // You can pattern-match the Result records via a Result cast
        var x =
                switch ((ResultSketch) res4) {
                    case ResultSketch.Ok o -> o.value();
                    case ResultSketch.Err e -> e.ex();
                };

        System.out.println("Err's default static exception: " + x);

        // var nope = res4.getChecked(); // This is like `get` but forces a checked exception for the Error

    }
}

// TODO: Maybe a static method to fillStack or whatever it's called,
//      letting users optionally pay the cost of turning a static exception into a dynamic exception with a full
// stacktrace
//      or even just getting a stacktrace for debugging.

/*
public int tryResult() {

    var r = Result.ok(42);
    assert(r.get() == 42);

    var e = Result.err(42, new Exception("this is an exception"));
    //return e.getChecked();

    //return switch(r) {
    //    case Ok(var value) -> value;
    //    case Err(var value, var ex) -> throw ex;
    //};


    return r.get();
}
*/

// Other implementations

// This one works, but it assumes you're always using runtime, unchecked exceptions

/*
public sealed interface Result<T> {

    public T value();
    default T get() {
        return switch (this) {
            case Ok<T> o  -> o.value();
            case Err<T> e -> throw new RuntimeException(e.ex());
        };
    }

    record Ok<T>(T value) implements Result<T>{}
    record Err<T>(T value, Throwable ex) implements Result<T>{}

    public static final Exception resultException = new Exception("Error Result");

    public static <T> Ok<T> ok(T value) {
        return new Ok<>(value);
    }

    public static <T> Err<T> err(T value) {
        return new Err<>(value, resultException);
    }

    public static <T> Err<T> err(T value, Throwable ex) {
        return new Err<>(value, ex);
    }
}
*/

// This one also works, but getChecked is generic Throwable
/*
public sealed interface Result<T> {

    public T value();
    default T get() {
        return switch (this) {
            case Ok<T> o  -> o.value();
            case Err<T,?> e -> throw new RuntimeException(e.ex());
        };
    }

    //default <E extends Throwable> T getChecked() throws E {
    default T getChecked() throws Throwable {
        // this works but the exception isn't checked at the call-site

        //if (this instanceof final Ok<T> o) {
        //    return o.value();
        //} else {
        //    Err<T,E> e = (Err<T,E>) this;
        //    throw e.ex();
        //}

        return switch (this) {
            case Ok<T> o  -> o.value();
            case Err<T,?> e -> Result.throwErr(e);
        };
    }

    record Ok<U>(U value) implements Result<U>{}
    record Err<U, E extends Throwable>(U value, E ex) implements Result<U>{}

    public static final Exception resultException = new Exception("Error Result");

    public static <U> Ok<U> ok(U value) {
        return new Ok<>(value);
    }

    public static <U> Err<U,Exception> err(U value) {
        return new Err<>(value, resultException);
    }

    public static <U, E extends Throwable> Err<U,E> err(U value, E ex) {
        return new Err<>(value, ex);
    }

    public static <U, E extends Throwable> U throwErr(Err<U,E> e) throws E {
        throw e.ex();
    }
}
*/
