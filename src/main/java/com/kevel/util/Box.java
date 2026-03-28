package com.kevel.util;

import java.util.Objects;
import java.util.function.Supplier;

/*
 * Boxes are a simple typed-tagged Reference Type, where the tags are removed
 * at compile time; zero-sized.
 * Boxes are immutable; This is a value-based class.  The contents of a Box
 * cannot be reset after creation.
 *
 * -- Tagging generic data --
 * Programming with generic data containers (Maps, Lists, etc.) is quite useful
 * and avoids the overhead of constant "domain object" marshalling.
 * Nonetheless, it can be useful to say, "This map represents an X" and
 * Boxes provide that.
 * The type tag can also be ignored since a Box is also
 * a Supplier (ie: a Box without the type tag).
 *
 * -- Newtype / Phantom types --
 * Boxes separate a concrete data representation (eg: a String, a list, etc.)
 * from a logical type.
 * "The newtype idiom gives compile time guarantees that the right logical
 * type of value is supplied to a program." - from: https://doc.rust-lang.org/rust-by-example/generics/new_types.html
 * For example, a person's age should be in "Years", represented by an int,
 * but you only care about that at compile time (the program fundamentally uses int).
 * See also: https://doc.rust-lang.org/rust-by-example/generics/phantom.html
 *
 * -- Lightweight capabilities --
 * As an extension of 'newtype', Boxes enable tracking and enforcing capabilities.
 * For example, you could tag a file a Readable and enforce read access.
 * Another example, you could tag a User record as an "Admin", such that it can
 * only perform admin operations (enforced at compile-time).
 * Optionally, Witness Objects enable confirmation at runtime that the capability was not forged.
 * Make sure witness objects have private visibility, scoped to where you need trust.
 *
 * -- Option type --
 * Boxes can be used as a simple Option type, removing null references from your program.
 */

public final class Box<Tag, T> implements Supplier<T> {
    private final T value;
    private final Object witness;
    private static final Object DEFAULT_WITNESS = new Object();
    public static final Box<?, ?> EMPTY = new Box(null);

    private Box() {
        this(null, DEFAULT_WITNESS);
    }

    private Box(T value) {
        this(value, DEFAULT_WITNESS);
    }

    private Box(T value, Object witness) {
        this.value = value;
        this.witness = witness;
    }

    public static <Tag, T> Box<Tag, T> of(T value) {
        Objects.requireNonNull(value, "Box value cannot be null. Use Box.EMPTY if you want an empty box");
        return new Box<>(value);
    }

    public static <Tag, T> Box<Tag, T> of(T value, Object witness) {
        Objects.requireNonNull(value, "Box value cannot be null. Use Box.EMPTY if you want an empty box");
        Objects.requireNonNull(witness, "Box witness cannot be null.");
        return new Box<>(value, witness);
    }

    public T get() {
        return value;
    }

    public T or(T other) {
        if (value == null) { // this is EMPTY
            return other;
        }
        return value;
    }

    public boolean hasWitness(Object expected) {
        return this.witness == expected;
    }

    public <X, R extends Comparable<T>> int compareTo(Box<X, R> other) {
        /*
         * See: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Comparable.html
         *
         * Even though Box isn't Comparable (because it might not hold a Comparable thing),
         * this method adheres to the rules and suggestions.
         * Things that comparable should be consistent with `equal`, which is
         * dynamic/flexible/weaker with Box.
         *
         * Use `tcompareTo` for a stricter Comparison.
         */
        return -(other.get().compareTo(value));
    }

    public <X extends Tag, R extends Comparable<T>> int tcompareTo(Box<X, R> other) {

        // Note: This has _slightly_ different behavior than `tequals`,
        //       since sub type tags are allowed to be compared whereas
        //       `tequals` requires the exact same type tag.
        //       This is on purpose.

        return -(other.get().compareTo(value));
    }

    /**
     * Create a new box with a new tag, but retain the value.
     * The new Box will drop the witness information, since its creation can't be trusted.
     */
    public <NewTag> Box<NewTag, T> into() {
        return new Box<>(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof Box b) {
            var otherValue = b.get();
            return Objects.equals(value, otherValue);
        }
        return false;
    }

    public boolean tequals(Box<Tag, T> other) {
        return Objects.equals(value, other.get());
    }

    @Override
    public int hashCode() {
        // Boxes are transparent, they have the same hashcode as the contents
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Box[" + value + "]";
    }
}
