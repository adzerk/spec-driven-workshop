package com.kevel.util;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A Result type that represents either success (Ok) or failure (Err).
 * Results are Values returned from functions,
 * enabling us to handle errors as Values/data and avoid exception throwing.
 *
 * This Result implementation bridges between Results and Exceptions.
 *
 * From Real World OCaml, 2e
 * Chapter 7: Error Handling
 *
 * > "Choosing an Error-Handling Strategy"
 * > https://dev.realworldocaml.org/error-handling.html#choosing-an-error-handling-strategy
 *
 * > If you’re writing a rough-and-ready program where getting it done quickly
 * > is key and failure is not that expensive, then using exceptions extensively
 * > may be the way to go. If, on the other hand, you’re writing production
 * > software whose failure is costly, then you should probably lean in the
 * > direction of using error-aware return types.
 *
 * > To be clear, it doesn’t make sense to avoid exceptions entirely.
 * > The maxim of “use exceptions for exceptional conditions” applies.
 * > If an error occurs sufficiently rarely, then throwing an exception
 * > is often the right behavior.
 *
 * > Also, for errors that are omnipresent, error-aware return types may be overkill.
 * > A good example is out-of-memory errors, which can occur anywhere, and so
 * > you’d need to use error-aware return types everywhere to capture those.
 * > Having every operation marked as one that might fail is no more explicit
 * > than having none of them marked.
 *
 * > In short, for errors that are a foreseeable and ordinary part of the
 * > execution of your production code and that are not omnipresent,
 * > error-aware return types are typically the right solution.
 *
 * ### How to use Results
 *
 * 1. Define the Error types
 * This can be done with a simple `enum` for basic error signals
 * ```
 * enum ValidationError {
 *   INVALID_USERNAME,
 *   MISSING_PASSWORD
 * }
 * ```
 *
 * Or you can use sealed interfaces to create Errors that carry data / Use a sum-type
 * ```
 * sealed interface ParsingError {
 *   record MissingToken(String input) implements ParsingError {}
 *   record UnrecognizedSymbol(String input, String symbol, Map context) {}
 * }
 *
 * 2. Return Results from methods instead of throwing exceptions
 *
 * 3. Handle Results using `switch` or the Result methods (`map`, `flatMap`, `fold`)
 * ```
 * var x = switch(someResult) {
 *   case Ok(var value) -> ...;
 *   case Err(var error) -> ...;
 * };
 * ```
 * And you can further switch/match on the Error types you created.
 *
 * @param <T> The type of the success value
 * @param <E> The type of the error value
 */
public sealed interface Result<T, E> extends Supplier<T> {

    /**
     * Creates a successful Result containing a value.
     *
     * @param value The success value
     * @param <T> The type of the success value
     * @param <E> The type of the error value
     * @return A Result containing the success value
     */
    @CheckReturnValue // must-use
    static <T, E> Result<T, E> ok(T value) {
        return new Ok<>(value);
    }

    /**
     * Creates a failed Result containing an error.
     *
     * @param error The error value
     * @param <T> The type of the success value
     * @param <E> The type of the error value
     * @return A Result containing the error value
     */
    @CheckReturnValue // must-use
    static <T, E> Result<T, E> err(E error) {
        return new Err<>(error);
    }

    /**
     * Wraps a potentially exception-throwing operation in a Result.
     *
     * @param supplier The operation that might throw
     * @param <T> The type of the success value
     * @param <E> The type of the Exception if thrown by supplier
     * @return A Result containing either the success value or error
     */
    @CheckReturnValue // must-use
    static <T, E extends Exception> Result<T, E> of(ThrowingSupplier<T> supplier) {
        try {
            return ok(supplier.get());
        } catch (Exception e) {
            return err((E) e);
        }
    }

    /**
     * Wraps a potentially exception-throwing operation in a Result.
     *
     * @param supplier The operation that might throw
     * @param errorMapper Function to convert Exception to error type
     * @param <T> The type of the success value
     * @param <E> The type of the error value
     * @return A Result containing either the success value or mapped error
     */
    @CheckReturnValue // must-use
    static <T, E> Result<T, E> of(ThrowingSupplier<T> supplier, Function<Exception, E> errorMapper) {
        try {
            return ok(supplier.get());
        } catch (Exception e) {
            return err(errorMapper.apply(e));
        }
    }

    /**
     * Returns true if this Result is Ok.
     */
    boolean isOk();

    /**
     * Returns true if this Result is Err.
     */
    boolean isErr();

    /**
     * Returns the success value if Ok, or throws if Err.
     *
     * @throws IllegalStateException if this Result is Err
     */
    T get();

    /**
     * Returns the success value if Ok, or the provided default if Err.
     */
    T orElse(T defaultValue);

    /**
     * Returns the success value if Ok, or computes a default from the error if Err.
     */
    T orCompute(Function<E, T> fn);

    /**
     * Maps the success value using the provided function.
     * If this Result is Err, returns the error unchanged.
     */
    <U> Result<U, E> map(Function<T, U> fn);

    /**
     * Applies a function that returns a Result to the success value.
     * This is used for chaining operations that may fail.
     * Also known as andThen.
     */
    <U> Result<U, E> flatMap(Function<T, Result<U, E>> fn);

    /**
     * Fold the Result into a destination R.
     * Executes onOk if this is Ok, onErr if this is Err.
     *
     * @param onOk Function to apply to success value
     * @param onErr Function to apply to error value
     * @return The result of applying the appropriate function
     */
    <R> R fold(Function<? super T, ? extends R> onOk, Function<? super E, ? extends R> onErr);

    /**
     * Converts this Result to an Optional.
     * Returns Optional.of(value) if Ok, Optional.empty() if Err.
     */
    Optional<T> toOptional();

    /**
     * Ok variant of Result containing a success value.
     */
    record Ok<T, E>(T value) implements Result<T, E> {
        public Ok {
            Objects.requireNonNull(value, "Ok value cannot be null");
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public T orElse(T defaultValue) {
            return value;
        }

        @Override
        public T orCompute(Function<E, T> fn) {
            return value;
        }

        @Override
        public <U> Result<U, E> map(Function<T, U> fn) {
            return ok(fn.apply(value));
        }

        @Override
        public <U> Result<U, E> flatMap(Function<T, Result<U, E>> fn) {
            return fn.apply(value);
        }

        @Override
        public <R> R fold(Function<? super T, ? extends R> onOk, Function<? super E, ? extends R> onErr) {
            return onOk.apply(value);
        }

        @Override
        public Optional<T> toOptional() {
            return Optional.of(value);
        }
    }

    /**
     * Err variant of Result containing an error value.
     */
    record Err<T, E>(E error) implements Result<T, E> {
        public Err {
            Objects.requireNonNull(error, "Err value cannot be null");
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        public T get() {
            return switch (error) {
                case Exception e -> Result.sneakyThrow(e);
                default -> throw new IllegalStateException("Called get() on Err value: " + error);
            };
        }

        @Override
        public T orElse(T defaultValue) {
            return defaultValue;
        }

        @Override
        public T orCompute(Function<E, T> fn) {
            return fn.apply(error);
        }

        @Override
        public <U> Result<U, E> map(Function<T, U> fn) {
            return err(error);
        }

        @Override
        public <U> Result<U, E> flatMap(Function<T, Result<U, E>> fn) {
            return err(error);
        }

        @Override
        public <R> R fold(Function<? super T, ? extends R> onOk, Function<? super E, ? extends R> onErr) {
            return onErr.apply(error);
        }

        @Override
        public Optional<T> toOptional() {
            return Optional.empty();
        }
    }

    /**
     * Functional interface for operations that may throw exceptions.
     */
    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * This is like get(), but if the Err type is holding an Exception, throw it as a checked exception
     */
    static <T, E extends Throwable> T getChecked(Result<T, E> result) throws E {
        return switch (result) {
            case Ok(var value) -> value;
            case Err(var error) -> throw error;
        };
    }

    @SuppressWarnings("unchecked")
    private static <R, X extends Throwable> R sneakyThrow(Throwable throwable) throws X {
        throw (X) throwable;
    }
}
