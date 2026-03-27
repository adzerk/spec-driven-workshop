
package com.kevel.util;

import java.util.function.Supplier;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import com.kevel.util.Box;
import com.kevel.util.NType;

public class States {

    /* We want to encode a State Machine in Java, such that the compiler
     * ensures there are only valid states and transitions.
     *
     * Let's look at two examples, Traffic Lights and File Systems.
     *
     * Imagine we have a simple traffic light:
     * As long as there's power, it transitions from Green, to Yellow, to Red.
     * Red transitions back to Green.
     * If at any point the traffic light loses power, it transitions to Flashing Red.
     * When power is restored, the light returns to Red.
     *
     * Now imagine we're at an intersection with two connected traffic lights.
     * When one light is Flashing Red, they both must be Flashing Red.
     * When one light is Green, the other must be Red.
     * When one light is Yellow, the other must be Red.
     * Both lights can be Red.
     * If both lights are Red, the light with more traffic gets Green first.
     * If the Intersection experiences a fault, both lights will be Flashing Red.
     *
     * - - - - -
     *
     * Imagine we have a simple file system.
     * We can create a File object.  When created it's automatically opened.
     * We can open a File.  When opened it's in a Ready state
     * A Ready File can be made Readable xor Writeable.
     * We can read from a Readable file, but we can't write.
     * We can write to a Writeable file, but we can't read.
     * Both Readable and Writeable can be marked "done" and return to a Ready File.
     * At any state, the file can be Closed.
     * A Closed file can only be opened again into the Ready File state.
     * At any state, we can fetch metadata information about the File
     */

    /* Some helpful links (including Rust state machines)
     * https://www.baeldung.com/java-enum-simple-state-machine
     * https://blog.yoshuawuyts.com/state-machines-2/
     * https://docs.rust-embedded.org/book/static-guarantees/design-contracts.html
     */

    // Let's start with the traditional way simple state machines get encoded in Java.
    // There's a drawback though, generic types aren't allowed on enum constants,
    // so we can't encode the 'transition' into the signatures.
    // That aside, this passes the test - only valid states and transitions are allowed.

    public enum TrafficLight {

        Green {
            @Override
            public TrafficLight nextState() {
                return Yellow;
            }
        },
        Yellow {
            @Override
            public TrafficLight nextState() {
                return Red;
            }
        },
        Red {
            @Override
            public TrafficLight nextState() {
                return Green;
            }
        },
        FlashingRed {
            @Override
            public TrafficLight nextState() {
                return Red;
            }
        };

        // It's discouraged to mix the `state` with the transitions,
        // but we're going to make an exception here since the
        // `state` is small and contained only within this TrafficLight
        private boolean powered = true;

        public boolean hasPower() {
            return this.powered;
        }

        public TrafficLight togglePower() {
            // Bundled together the action ("loss of power") with the state
            // transition.
            // This centralizes the logic in one spot instead of having each
            // `nextState` check if `this.hasPower()`.
            this.powered = !this.powered;
            if (this.hasPower()) {
                return Red;
            } else {
                return FlashingRed;
            }
        }

        public abstract TrafficLight nextState();
    }

    // Here's another way to do enum-style where transitions are enforced at runtime:
    // https://gist.github.com/gabrielbauman/42ac75757146af9e864f334221f2be06


    /*
     * Looking at another approach...
     */

    public interface Light {};
    public interface G extends Light{}; // Green
    public interface Y extends Light{}; // Yellow
    public interface R extends Light{}; // Red
    public interface F extends Light{}; // FlashingRed

    public interface TransitionsTo<T> {
        public T nextState();
    }

    public interface DispatchTo<T> {
        interface TransitionOne<T> extends Supplier<T>{}
        default T dispatch(TransitionOne<T> supplier) {
            return supplier.get();
        }
    }
    public interface BiDispatchTo<T,U> extends DispatchTo<T> {
        interface TransitionTwo<U> extends Supplier<U>{}
        default U dispatch(TransitionTwo<U> supplier) {
            return supplier.get();
        }
    }

    // We'll make the valid states with a sumtype interface
    public sealed interface Intersection<L1 extends Light, L2 extends Light> {


        // You can add these if you want the type information available at Runtime
        //public TrafficLight lightOne();
        //public TrafficLight lightTwo();

        final static class Unpowered implements Intersection<F,F>, TransitionsTo<Intersection<F,F>> {
            public Unpowered nextState() {
                return this;
            }
            /*
            public TrafficLight lightOne() {
                return TrafficLight.FlashingRed;
            }
            public TrafficLight lightTwo() {
                return TrafficLight.FlashingRed;
            }
            */
        }

        public final static class GR implements Intersection<G,R>, TransitionsTo<Intersection<Y,R>> {
            public YR nextState() {
                return new YR();
            }
        }

        public final static class YR implements Intersection<Y,R>, TransitionsTo<Intersection<R,G>> {
            public RG nextState() {
                return new RG();
            }
        }


        public final static class RR implements Intersection<R,R>,
                                                TransitionsTo<Intersection<G,R>>,
                                                BiDispatchTo<GR,RG> {

            /* Provide a default transition, but enable an outside caller
             * to `dispatch` this transition at runtime, while still confirming
             * the dispatch was valid at compile time.
             */

            public GR nextState() {
                return new GR();
            }
        }

        public final static class RY implements Intersection<R,Y>, TransitionsTo<Intersection<G,R>> {
            public GR nextState() {
                return new GR();
            }
        }

        public final static class RG implements Intersection<R,G>, TransitionsTo<Intersection<R,Y>> {
            public RY nextState() {
                return new RY();
            }
        }

        public final static class FF implements Intersection<F,F>, TransitionsTo<Intersection<R,R>> {
            public RR nextState() {
                return new RR();
            }
        }

        static Unpowered powerless = new Unpowered();

        static RR init() {
            return new RR();
        }

        static RR togglePower(Unpowered up) {
            return new RR();
        }
        static Unpowered togglePower(Intersection<?,?> i) {
            return powerless;
        }

        static boolean hasPower(Intersection<?,?> i) {
            return !(i instanceof Unpowered);
        }

        static FF fault(Intersection<?,?> i) {
            return new FF();
        }
    }

    public boolean isGR(Intersection<G,R> gr) {
        return true; // always true because compiler enforced this
    }

    public boolean typeExample () {
        var intersection = Intersection.init()       // R,R
                                       .nextState()  // G,R
                                       .nextState(); // Y,R
        var midState = Intersection.togglePower(intersection); // F,F
        var next = Intersection.togglePower(midState); //R,R
        var endState = next.nextState();               //G,R

        // Let's see an example where we can control the transitions
        var anotherIntersection = Intersection.init()  // R,R
                                              .dispatch(Intersection.RG::new) // Compiler enforced
                                              .nextState(); // R,Y

        return isGR(endState);
    }

    public class FileSystemExample {

        /* A simple filesystem example that controls reading/writing */

        public interface FileStatus extends NType {}
        public interface Invalid extends FileStatus {}
        public interface Ready extends FileStatus {}
        public interface IOable extends Ready {}
        public interface Readable extends IOable {}
        public interface Writable extends IOable {}
        public interface Closed extends FileStatus {}


        /*
        public static Box<? extends FileStatus,Path> open(Path path) {
            if (Files.exists(path)) {
                return new Box<Ready,Path>(path);
            }
            return Box.of(null, Invalid.class);
        }
        */

        public static Box<Ready,Path> open(Path path) {
            if ((path != null) && (Files.exists(path))) {
                return new Box<Ready,Path>(path);
            }
            return null;
        }

        public static Box<Readable,Path> readable(Box<Ready,Path> b) {
            return switch(b) {
                case Box(var p) when Files.isReadable(p) -> b.into(Readable.class);
                default -> null;
            };
        }

        public static Box<Writable,Path> writable(Box<Ready,Path> b) {
            return switch(b) {
                case Box(var p) when Files.isWritable(p) -> b.into(Writable.class);
                default -> null;
            };
        }

        public static byte[] read(Box<Readable,Path> b) {
            try {
                if (!Box.isEmpty(b)) {
                    return Files.readAllBytes(b.get());
                }
            } catch(Exception e) {}
            return null;
        }

        public static Path write(Box<Writable,Path> b, byte[] bytes) {
            try {
                if (!Box.isEmpty(b)) {
                    return Files.write(b.get(), bytes, StandardOpenOption.APPEND);
                }
            } catch(Exception e) {}
            return null;
        }

        public static <T extends IOable> Box<Ready,Path> done(Box<T,Path> b) {
            if (b != null) {
                return b.into(Ready.class);
            }
            return null;
        }

        public static <T extends Ready> Box<Closed,Path> close(Box<T,Path> b) {
            if (b != null) {
                return b.into(Closed.class);
            }
            return null;
        }

        public static String fileInfo(Supplier<Path> b) {
            try {
                return "Owner: "+Files.getOwner(b.get());
            } catch(Exception e) {
                return null;
            }
        }

        public static void example() {
            var x = open(Path.of("/tmp/stuff"));
            var readableX = readable(x);
            var stuff = read(readableX);
            //var canIWrite = write(readableX); // compile-time error
            //var illegalTransition = writable(readableX); // compile-time error
            var xAgain = done(readableX);
            var owner = fileInfo(xAgain);
            var closed = close(xAgain);
        }

    }
}


/*
// This is neat, but technically allows invalid intersections to be constructed
// Marking it as `private` inside another class, potentially with a static `Map.of()`
// to create the valid states would work nicely though.
// That would be similar to how immutable data is used in Clojure to solve the problem
public record Intersection<L1 extends Light,L2 extends Light>(TrafficLight light1, TrafficLight light2) {


        public Intersection() {
            this(TrafficLight.Red, TrafficLight.Red);
        }

        public static Intersection of(TrafficLight l1, TrafficLight l2) {
            return new Intersection(l1, l2);
        }

        public Intersection nextState() {
            return switch(this) {
                case Intersection<>(TrafficLight.Red, TrafficLight.Red) -> Intersection.of(TrafficLight.Green, TrafficLight.Red);
                case Intersection<>(TrafficLight.Green, TrafficLight.Red) -> Intersection.of(TrafficLight.Yellow, TrafficLight.Red);
                case Intersection<>(TrafficLight.Red, TrafficLight.Green) -> Intersection.of(TrafficLight.Red, TrafficLight.Yellow);
                default -> Intersection(light1.nextState(), light2.nextState());
            };
        }
}
*/
