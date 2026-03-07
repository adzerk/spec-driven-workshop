package com.kevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void increasingSum_expectedUsage() {
        assertEquals(30, App.increasingSum(10, 20));
        assertEquals(40, App.increasingSum(20, 20));
        assertEquals(21, App.increasingSum(3, 20));
    }

    @Property
    void increasingSum_matchesMethodContract(@ForAll("validIncreasingInputs") IntPair input) {
        int result = App.increasingSum(input.x(), input.y());

        if (input.y() % input.x() == 0) {
            assertEquals(input.x() + input.y(), result);
        } else {
            assertEquals(input.y() + 1, result);
        }
    }

    @Property
    void increasingSum_isAlwaysGreaterThanY(@ForAll("validIncreasingInputs") IntPair input) {
        int result = App.increasingSum(input.x(), input.y());
        assertTrue(result > input.y());
    }

    @Provide
    Arbitrary<IntPair> validIncreasingInputs() {
        Arbitrary<Integer> xValues = Arbitraries.integers().between(1, 100_000);

        return xValues.flatMap(x -> {
            int maxY = Integer.MAX_VALUE - x;
            if (maxY < x) {
                return Arbitraries.of();
            }

            return Arbitraries.integers().between(x, maxY).map(y -> new IntPair(x, y));
        });
    }

    private record IntPair(int x, int y) {}
}
