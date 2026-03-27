
package com.kevel.util;

import java.util.function.Supplier;

import com.kevel.util.NType;

/*
 * Boxes are a simple typed-tagged Reference Type, where the tags are removed
 * at compile time; zero-sized.
 * Boxes are immutable; This is a value-based record.  The contents of a Box
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
 *
 * -- Option type --
 * Boxes can be used as a simple Option type, removing null references from your program.
 */

public record Box<W extends NType,T>(T inner) implements Supplier<T> {

    public static final Box<NType,?> EMPTY = new Box<>();

    private Box(){
        this(null);
    }

    public Box(T inner) {
        this.inner = inner;
    }

    public T get() {
        return this.inner;
    }

    public static <X extends NType,R> Box<X,R> of(R inner) {
        if ((inner == null) || (inner == EMPTY)) {
            @SuppressWarnings("unchecked")
            Box<X,R> b = (Box<X,R>) EMPTY;
            return b;
        }
        return new Box(inner);
    }

    public static <X extends NType,R> Box<X,R> of(R inner, Class<X> x) {
        if ((inner == null) || (inner == EMPTY)) {
            @SuppressWarnings("unchecked")
            Box<X,R> b = (Box<X,R>) EMPTY;
            return b;
        }
        return new Box(inner);
    }

    //TODO: Maybe make this an instance method
    public static boolean isEmpty(Box b) {
        // Defend against someone using the constructor to make an empty box
        return ((b == null) || (b == EMPTY) || (b.get() == null));
    }

    public <X extends NType> Box<X,T> into(Class<X> x) {
        return new Box<X,T>(inner);
    }

    // This works nicely with `var` and wildcard captures
    public <R> R orElse(R other) {
        if (this.inner == null) {
            return other;
        }
        return (R)this.inner;
    }

    public <X extends NType, R extends Comparable<T>> int compareTo(Box<X,R> other) {
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
        return -(other.get().compareTo(this.inner));
    }

    public <X extends W, R extends Comparable<T>> int tcompareTo(Box<X,R> other) {

        // Note: This has _slightly_ different behavior than `tequals`,
        //       since sub type tags are allowed to be compared whereas
        //       `tequals` requires the exact same type tag.
        //       This is on purpose.

        return -(other.get().compareTo(this.inner));
    }

    public <R> boolean tequals(Box<W,R> other) {
        return this.inner.equals(other.get());
    }

    class BoxExample {
        public interface Email extends NType {}
        public interface Password extends NType {}
        public interface Age extends NType {}
        public interface AllowAge extends Age {}

        public <T> T checkEmail(Box<Email,T> email) {
            return email.get();
        }

        public boolean tryIt() {
            // Let's make some Boxes
            var someEmail = new Box<Email,String>("hello");
            Box<Email,?> anotherEmail = Box.of("goodbye");
            var somePassword = new Box<Password,String>("world");
            var anotherPassword = Box.of("terre", Password.class);

            // The compiler ensures only "Email" things are passed to checkEmail
            var res =  checkEmail(someEmail) == "hello"; // -> true
            //checkEmail(somePassword) // Compile-time error

            // `tequals` is a typed `equals`. Compiler only allows same type tag
            var anotherRes = res && someEmail.tequals(anotherEmail);
            //someEmail.tequals(somePassword); // Compile-time error
            // But `equals` is dynamic, as expected:
            someEmail.equals(somePassword); // this compares the `inner` values of our Box record

            // Let's make three empty boxes
            var emptyBox = Box.EMPTY;
            var anotherEmptyBox = Box.of(null);
            var badEmptyBox = new Box<>(null); // Careful with the Constructor!
            emptyBox.tequals(anotherEmptyBox); // and .equals()
            emptyBox.tequals(badEmptyBox);     // and .equals()
            var comp = ((emptyBox == anotherEmptyBox) &&
                        (emptyBox != badEmptyBox));
            // But all their hashcodes are the same, `0` -- they're all hashed as `null`

            // Boxes work as an Option type
            var sum = emptyBox.orElse(11) + 100;
            var addr = someEmail.orElse("default@domain.com").toUpperCase();

            // Boxes can be destructured with `switch`
            var someAge = new Box<Age,Integer>(99);
            var matchRes = switch(someAge) {
                //case Box<Password,?>(var pw) -> "We have a password: "+pw.toString(); // Compile-time error
                // We don't need to use the generics, but the types are enforced as we saw on the prev line
                case Box<Age,Integer>(var i) when i > 10 -> "We have an allowable age";
                default -> "Unallowed age";
            };

            // Boxes are transparent (following the same rules as Records)
            assert (someEmail.hashCode() == "hello".hashCode());

            // Boxes that contain Comparable things can be compared.
            var stringComp = someEmail.compareTo(somePassword);
            // `tcompareTo` is a typed `compareTo`. Compiler only allows sub type tag
            //var strictComp = someEmail.tcompareTo(somePassword); // Compile-time error
            var anotherAge = new Box<AllowAge,Integer>(100);
            var ageComp = someAge.tcompareTo(anotherAge); // AllowAge extends Age

            return true;
        }
    }
}

