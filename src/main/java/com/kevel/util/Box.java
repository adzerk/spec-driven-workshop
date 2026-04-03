package com.kevel.util;

import com.google.errorprone.annotations.CheckReturnValue;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A lightweight tagged wrapper that preserves the runtime representation of a value while attaching
 * compile-time semantics.
 *
 * <p>A {@code Box<Tag, T>} supports three closely related use cases:
 *
 * <ul>
 *   <li>newtype-style logical tagging over a simple runtime representation;
 *   <li>capability tracking through a witness token checked by identity; and
 *   <li>a bounded optional-like empty state for APIs that choose {@code Box} over {@code Optional}.
 * </ul>
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>a non-empty box always contains a non-null value;
 *   <li>a witnessed box always stores a non-null witness;
 *   <li>emptiness is represented only by the shared empty box instance shape;
 *   <li>the tag parameter affects compile-time reasoning only and is not reified at runtime.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * interface RawUserId {}
 * interface ValidatedUserId {}
 *
 * Box<RawUserId, String> raw = Box.of("user-123");
 * Box<ValidatedUserId, String> validated = raw.into();
 * }</pre>
 *
 * <p>Boxes are a simple typed-tagged Reference Type, where the tags are removed
 * at compile time; zero-sized.
 * <br>Boxes are immutable; This is a value-based class.  The contents of a Box cannot be reset after creation.
 *
 * <p>-- Tagging generic data --
 * <p>Programming with generic data containers (Maps, Lists, etc.) is quite useful
 * and avoids the overhead of constant "domain object" marshalling.
 * Nonetheless, it can be useful to say, "This map represents an X" and
 * Boxes provide that.
 * The type tag can also be ignored since a Box is also
 * a Supplier (ie: a Box without the type tag).
 *
 * <p>-- Newtype / Phantom types --
 * <p>Boxes separate a concrete data representation (eg: a String, a list, etc.)
 * from a logical type.
 * <br>"The newtype idiom gives compile time guarantees that the right logical
 * type of value is supplied to a program." - from: https://doc.rust-lang.org/rust-by-example/generics/new_types.html
 * For example, a person's age should be in "Years", represented by an int,
 * but you only care about that at compile time (the program fundamentally uses int).
 * <br>See also: https://doc.rust-lang.org/rust-by-example/generics/phantom.html
 *
 * <p>-- Lightweight capabilities --
 * <p>As an extension of 'newtype', Boxes enable tracking and enforcing capabilities.
 * For example, you could tag a file a Readable and enforce read access.
 * Another example, you could tag a User record as an "Admin", such that it can
 * only perform admin operations (enforced at compile-time).
 * Optionally, Witness Objects enable confirmation at runtime that the capability was not forged.
 * Make sure witness objects have private visibility, scoped to where you need trust.
 *
 * <p>-- Option type --
 * <p>Boxes can be used as a simple Option type, removing null references from your program.
 *
 * @param <Tag> compile-time logical tag or capability marker
 * @param <T> wrapped runtime value type
 */
public final class Box<Tag, T> implements Supplier<T> {
    private static final Object DEFAULT_WITNESS = new Object();
    private static final Box<?, ?> EMPTY = new Box(null, DEFAULT_WITNESS);

    private final T value;
    private final Object witness;

    private Box(T value, Object witness) {
        this.value = value;
        this.witness = witness;
    }

    /**
     * Creates a non-empty box with the default internal witness.
     *
     * <p>Preconditions: {@code value} must be non-null.
     *
     * <p>Postconditions: returns a new box whose {@link #get()} is {@code value} and whose witness
     * is the default witness defined by this class.
     *
     * <p>Safety requirements: callers must not use this method to represent absence; use
     * {@link #empty()} for that case.
     *
     * @param value wrapped value; must be non-null
     * @param <Tag> compile-time tag for the returned box
     * @param <T> runtime value type
     * @return a new non-empty box containing {@code value}
     * @throws NullPointerException if {@code value} is null
     */
    @CheckReturnValue // must-use
    public static <Tag, T> Box<Tag, T> of(T value) {
        Objects.requireNonNull(value, "Box value cannot be null. Use Box.empty() if you want an empty box");
        return new Box<>(value, DEFAULT_WITNESS);
    }

    /**
     * Creates a non-empty box with an explicit witness token.
     *
     * <p>Preconditions: {@code value} and {@code witness} must both be non-null.
     *
     * <p>Postconditions: returns a new box whose {@link #get()} is {@code value} and whose witness
     * compares by identity in {@link #hasWitness(Object)}.
     *
     * <p>Safety requirements: keep witness tokens private to the authority boundary that mints
     * them so callers cannot forge capabilities/tags.
     *
     * @param value wrapped value; must be non-null
     * @param witness authority token checked by identity; must be non-null
     * @param <Tag> compile-time tag for the returned box
     * @param <T> runtime value type
     * @return a new non-empty box containing {@code value} and {@code witness}
     * @throws NullPointerException if {@code value} or {@code witness} is null
     */
    @CheckReturnValue // must-use
    public static <Tag, T> Box<Tag, T> of(T value, Object witness) {
        Objects.requireNonNull(value, "Box value cannot be null. Use Box.empty() if you want an empty box");
        Objects.requireNonNull(witness, "Box witness cannot be null");
        return new Box<>(value, witness);
    }

    /**
     * Returns the wrapped value.
     *
     * <p>Postconditions: returns the stored value for non-empty boxes and {@code null} for
     * {@link #empty()}.
     *
     * <p>Safety requirements: callers should prefer {@link #or(Object)} when absence is expected.
     *
     * @return the wrapped value, or {@code null} only for an empty box
     */
    @Override
    public T get() {
        return value;
    }

    /**
     * Returns the wrapped value when present, otherwise {@code other}.
     *
     * <p>Postconditions: returns {@link #get()} when this box is non-empty; otherwise returns
     * {@code other} unchanged.
     *
     * @param other fallback value used only when this box is empty
     * @return the wrapped value or {@code other}
     */
    public T or(T other) {
        return value == null ? other : value;
    }

    /**
     * Returns whether this box carries the expected witness token.
     *
     * <p>Postconditions: returns {@code true} only when the stored witness is the same object by
     * identity as {@code expected}.
     *
     * <p>Safety requirements: this check is identity-based, not {@code equals}-based, to avoid
     * forged authority.
     *
     * @param expected witness token to compare by identity
     * @return {@code true} when {@code expected} is the stored witness object
     */
    public boolean hasWitness(Object expected) {
        return witness == expected;
    }

    /**
     * Compares wrapped values across any two tags.
     * Box adheres to the general value-based rules for comparison and equality.
     * Calls {@code Integer.signum} on the return of compareTo; Do not program against the concrete values of compareTo.
     * Use {@code Box.tequals(...)} and {@code Box.tcompareValue(...)} for a stricter comparison.
     *
     * <p>Preconditions: both boxes must be non-empty and both wrapped values must participate in the
     * declared {@link Comparable} relation.
     *
     * <p>Postconditions: returns a negative number, zero, or a positive number according to the same
     * ordering as the wrapped values.
     *
     * <p>Safety requirements: empty boxes are rejected because ordering over absence is ambiguous.
     *
     * @param other other box to compare against; must be non-empty
     * @param <X> tag of the other box
     * @param <R> comparable value type of the other box
     * @return comparison result with the same sign as comparing wrapped values
     * @throws NullPointerException if {@code other} is null
     * @throws IllegalStateException if either box is empty
     */
    public <X, R extends Comparable<T>> int compareValue(Box<X, R> other) {
        Box<X, R> checkedOther = requireComparableOther(other, "compareTo");
        requirePresent(value, "compareTo requires a non-empty receiver. This Box had a value of 'null'.");
        return -Integer.signum(checkedOther.value.compareTo(value));
    }

    /**
     * Compares wrapped values when the other tag is assignment-compatible with this tag.
     *
     * <p>Preconditions: both boxes must be non-empty. Subtype-compatible tags are permitted by the
     * method signature.
     *
     * <p>Postconditions: returns a value with the same sign as the wrapped-value comparison.
     *
     * @param other other box to compare against; must be non-empty
     * @param <X> tag of the other box, constrained to this tag domain
     * @param <R> comparable value type of the other box
     * @return comparison result with the same sign as comparing wrapped values
     * @throws NullPointerException if {@code other} is null
     * @throws IllegalStateException if either box is empty
     */
    public <X extends Tag, R extends Comparable<T>> int tcompareValue(Box<X, R> other) {
        Box<X, R> checkedOther = requireComparableOther(other, "tcompareTo");
        requirePresent(value, "tcompareTo requires a non-empty receiver. This Box had a value of 'null'");
        return -Integer.signum(checkedOther.value.compareTo(value));
    }

    /**
     * Creates a new box of the same value with a new tag, but will drop the witness information.
     * This operation is used to "re-tag" a box.
     *
     * <p>Postconditions: returns a new box with the same wrapped value and a fresh default witness.
     *
     * <p>Safety requirements: use this only when the tag transition is valid by construction and the
     * old witness must not survive the boundary.
     *
     * @param <NewTag> destination compile-time tag
     * @return a new box with the same value and no transferred witness authority
     */
    @CheckReturnValue // must-use
    public <NewTag> Box<NewTag, T> into() {
        return new Box<>(value, DEFAULT_WITNESS);
    }

    /**
     * Creates a new box of the same value with a new tag, but resetting the witness information.
     * Re-tags this box and installs a replacement witness.
     *
     * <p>Preconditions: {@code witness} must be non-null.
     *
     * <p>Postconditions: returns a new box with the same wrapped value and the provided witness.
     *
     * <p>Safety requirements: callers are responsible for minting and containing the replacement
     * witness.
     *
     * @param witness replacement witness token; must be non-null
     * @param <NewTag> destination compile-time tag
     * @return a new box with the same value and the provided witness
     * @throws NullPointerException if {@code witness} is null
     */
    @CheckReturnValue // must-use
    public <NewTag> Box<NewTag, T> into(Object witness) {
        Objects.requireNonNull(witness, "Box witness cannot be null");
        return new Box<>(value, witness);
    }

    /**
     * Re-tags this box only when the caller proves possession of the stored witness.
     *
     * <p>Preconditions: {@code witness} must be the same object identity as the stored witness.
     *
     * <p>Postconditions: returns a new box with the same wrapped value and the same witness.
     *
     * <p>Safety requirements: this method enforces capability transfer at runtime using witness
     * identity.
     *
     * @param witness expected witness token
     * @param <NewTag> destination compile-time tag
     * @return a new box with the same value and preserved witness
     * @throws IllegalStateException if {@code witness} does not match the stored witness by identity
     */
    @CheckReturnValue // must-use
    public <NewTag> Box<NewTag, T> intoOnlyWith(Object witness) {
        if (hasWitness(witness)) {
            return new Box<>(value, witness);
        }
        throw new IllegalStateException("Attempt to shift a box with incorrect or invalid witness token");
    }

    /**
     * Compares equality of boxes by wrapped value only.
     * This behavior matches other value-oriented classes, like Records
     *
     * <p>Postconditions: tags and witnesses are ignored. Two boxes are equal when their wrapped
     * values are equal under {@link Objects#equals(Object, Object)}.
     *
     * @param other object to compare
     * @return {@code true} when {@code other} is a box with an equal wrapped value
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof Box<?, ?> otherBox) {
            return Objects.equals(value, otherBox.value);
        }
        return false;
    }

    /**
     * Compares equality of boxes with the same declared tag by wrapped value only.
     *
     * <p>Preconditions: {@code other} must be non-null.
     *
     * <p>Postconditions: returns the same result as applying {@link Objects#equals(Object, Object)}
     * to the wrapped values.
     *
     * @param other box with the same tag domain
     * @return {@code true} when both wrapped values are equal
     * @throws NullPointerException if {@code other} is null
     */
    public boolean tequals(Box<Tag, T> other) {
        Objects.requireNonNull(other, "tequals requires a non-null other box");
        return Objects.equals(value, other.value);
    }

    /**
     * Returns a hash code based only on the wrapped value.
     * This behavior matches other value-oriented classes, like Records
     *
     * <p>Postconditions: boxes that are equal under {@link #equals(Object)} produce the same hash
     * code.
     *
     * @return wrapped-value hash code
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /**
     * Returns a debugging representation that exposes only the wrapped value.
     *
     * @return a string in the form {@code Box[value]}
     */
    @Override
    public String toString() {
        return "Box[" + value + "]";
    }

    /**
     * Returns an empty box.
     *
     * @return a box whose inner value is null.
     */
    @SuppressWarnings("unchecked")
    public static <X, R> Box<X, R> empty() {
        return (Box<X, R>) EMPTY;
    }

    private static <T> T requirePresent(T candidate, String message) {
        if (candidate == null) {
            throw new IllegalStateException(message);
        }
        return candidate;
    }

    private static <X, T, R extends Comparable<T>> Box<X, R> requireComparableOther(
            Box<X, R> other, String methodName) {
        Objects.requireNonNull(other, methodName + " requires a non-null other box");
        requirePresent(other.value, methodName + " requires a non-empty argument");
        return other;
    }
}
