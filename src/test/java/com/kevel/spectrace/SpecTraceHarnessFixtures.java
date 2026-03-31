package com.kevel.spectrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

final class SpecTraceHarnessFixtures {

    private SpecTraceHarnessFixtures() {}

    static final class JupiterFixtures {

        @Test
        @SpecTrace({"TRACE-JUNIT"})
        void tracedJUnitMethodPasses() {
            assertEquals(2, 1 + 1);
        }

        @Test
        void unannotatedTestRemainsUnaffected() {
            assertTrue(true);
        }
    }

    static final class PropertyFixtures {

        @Property(tries = 3)
        @SpecTrace({"TRACE-PROPERTY", "TRACE-SECONDARY"})
        void tracedPropertySupportsMultipleIdentifiers(@ForAll("smallInts") int value) {
            assertTrue(value >= 0);
        }

        @Provide
        Arbitrary<Integer> smallInts() {
            return Arbitraries.integers().between(0, 3);
        }
    }

    static final class ReviewOnlyFixtures {

        @Test
        @SpecTrace({"TRACE-REVIEW-ONLY"})
        void reviewOnlyRequirementStillUsesATracedPassingTest() {
            // The requirement is verified by code review against documented authoring guidance.
            assertTrue(true);
        }
    }

    static final class CoverageFixtures {

        @Test
        @SpecTrace({"TRACE-UNUSED"})
        void coversTheRemainingIdentifier() {
            assertTrue(true);
        }
    }

    static final class ContinuationFixtures {

        static final AtomicInteger executedMethods = new AtomicInteger();

        static void reset() {
            executedMethods.set(0);
        }

        @Test
        @SpecTrace({"TRACE-UNKNOWN"})
        void unknownIdentifierFailsBeforeExecution() {
            executedMethods.incrementAndGet();
        }

        @Test
        @SpecTrace({"TRACE-JUNIT"})
        void validTracedMethodStillRuns() {
            executedMethods.incrementAndGet();
        }
    }

    static final class InvalidAnnotationFixtures {

        @Test
        @SpecTrace({"trace-junit"})
        void malformedIdentifierFails() {
            assertTrue(true);
        }

        @Test
        @SpecTrace({})
        void emptyAnnotationFails() {
            assertTrue(true);
        }
    }

    static final class UnannotatedFixtures {

        @Test
        void unannotatedMethodPasses() {
            assertTrue(true);
        }
    }
}
