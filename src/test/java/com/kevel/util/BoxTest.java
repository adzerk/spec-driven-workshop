package com.kevel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoxTest {

    interface Years {}

    interface Months {}

    interface Permission {}

    interface Admin extends Permission {}

    interface User {}

    @Test
    void ofStoresValueAndActsAsSupplier() {
        Box<Years, Integer> age = Box.of(42);

        assertEquals(42, age.get());
        assertEquals(42, ((java.util.function.Supplier<Integer>) age).get());
    }

    @Test
    void ofRejectsNullValues() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> Box.of(null));

        assertTrue(exception.getMessage().contains("Box value cannot be null"));
    }

    @Test
    void emptyProvidesATypedEmptyBox() {
        Box<Years, String> empty = Box.empty();

        assertNull(empty.get());
        assertEquals("fallback", empty.or("fallback"));
    }

    @Test
    void ofWithWitnessStoresWitnessByIdentity() {
        Object witness = new Object();
        Box<Admin, String> token = Box.of("allowed", witness);

        assertEquals("allowed", token.get());
        assertTrue(token.hasWitness(witness));
        assertFalse(token.hasWitness(new Object()));
    }

    @Test
    void ofWithWitnessRejectsNullWitness() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> Box.of("value", null));

        assertTrue(exception.getMessage().contains("Box witness cannot be null"));
    }

    @Test
    void emptyActsLikeAnOptionWithoutAValue() {
        Box<Years, String> empty = Box.empty();

        assertNull(empty.get());
        assertEquals("fallback", empty.or("fallback"));
        assertFalse(empty.hasWitness(new Object()));
    }

    @Test
    void nonEmptyOrReturnsContainedValue() {
        Box<Years, Integer> age = Box.of(12);

        assertEquals(12, age.or(99));
    }

    @Test
    void intoPreservesValueButDropsWitness() {
        Object witness = new Object();
        Box<Admin, String> adminToken = Box.of("alice", witness);

        Box<User, String> userView = adminToken.into();

        assertEquals("alice", userView.get());
        assertFalse(userView.hasWitness(witness));
        assertTrue(adminToken.hasWitness(witness));
    }

    @Test
    void intoWithWitnessPreservesValueAndResetsWitness() {
        Object originalWitness = new Object();
        Object replacementWitness = new Object();
        Box<Admin, String> original = Box.of("alice", originalWitness);

        Box<User, String> converted = original.into(replacementWitness);

        assertEquals("alice", converted.get());
        assertTrue(converted.hasWitness(replacementWitness));
        assertFalse(converted.hasWitness(originalWitness));
        assertTrue(original.hasWitness(originalWitness));
    }

    @Test
    void intoWithWitnessRejectsNullWitness() {
        Box<Admin, String> original = Box.of("alice", new Object());

        NullPointerException exception = assertThrows(NullPointerException.class, () -> original.into(null));

        assertTrue(exception.getMessage().contains("Box witness cannot be null"));
    }

    @Test
    void intoOnlyWithRequiresMatchingWitnessAndPreservesIt() {
        Object witness = new Object();
        Box<Admin, String> token = Box.of("alice", witness);

        Box<User, String> shifted = token.intoOnlyWith(witness);

        assertEquals("alice", shifted.get());
        assertTrue(shifted.hasWitness(witness));
        assertThrows(IllegalStateException.class, () -> token.intoOnlyWith(new Object()));
    }

    @Test
    void intoCreatesANewBoxInsteadOfMutatingTheOriginal() {
        Box<Years, Integer> original = Box.of(21);
        Box<Months, Integer> converted = original.into();

        assertNotSame(original, converted);
        assertEquals(21, original.get());
        assertEquals(21, converted.get());
    }

    @Test
    void equalsIgnoresTagAndWitnessAndComparesUnderlyingValue() {
        Box<Years, Integer> years = Box.of(5);
        Box<Months, Integer> months = Box.of(5, new Object());
        Box<Years, Integer> other = Box.of(7);

        assertEquals(years, months);
        assertEquals(years.hashCode(), months.hashCode());
        assertNotEquals(years, other);
        assertNotEquals(years, "not a box");
    }

    @Test
    void tequalsChecksBoxesWithTheSameTag() {
        Box<Years, Integer> left = Box.of(9);
        Box<Years, Integer> same = Box.of(9, new Object());
        Box<Years, Integer> different = Box.of(10);

        assertTrue(left.tequals(same));
        assertFalse(left.tequals(different));
    }

    @Test
    void toStringShowsUnderlyingValue() {
        assertEquals("Box[17]", Box.<Years, Integer>of(17).toString());
        assertEquals("Box[null]", Box.empty().toString());
    }

    @Test
    void compareValueMatchesContainedComparableOrderingAcrossTags() {
        Box<Years, Integer> younger = Box.of(18);
        Box<Months, Integer> older = Box.of(21);

        assertTrue(younger.compareValue(older) < 0);
        assertTrue(older.compareValue(younger) > 0);
        assertEquals(0, younger.compareValue(Box.<Months, Integer>of(18)));
    }

    @Test
    void compareValueRejectsEmptyBoxes() {
        Box<Years, Integer> years = Box.of(18);
        Box<Months, Integer> emptyMonths = Box.empty();

        assertThrows(IllegalStateException.class, () -> years.compareValue(emptyMonths));
        assertThrows(IllegalStateException.class, () -> emptyMonths.compareValue(years));
    }

    @Test
    void tcompareValueSupportsSubtypeCompatibleTags() {
        Box<Permission, String> permission = Box.of("editor");
        Box<Admin, String> admin = Box.of("owner");

        assertTrue(permission.tcompareValue(admin) < 0);
        assertTrue(admin.tcompareValue(permission.into()) > 0);
    }

    @Test
    void tcompareValueRejectsEmptyBoxes() {
        Box<Permission, String> permission = Box.of("editor");
        Box<Admin, String> emptyAdmin = Box.empty();

        assertThrows(IllegalStateException.class, () -> permission.tcompareValue(emptyAdmin));
        assertThrows(IllegalStateException.class, () -> emptyAdmin.tcompareValue(permission.into()));
    }

    @Property
    void intoPreservesUnderlyingValue(@ForAll("nonNullInts") int value) {
        Box<Years, Integer> years = Box.of(value);

        Box<Months, Integer> months = years.into();

        assertEquals(value, months.get());
    }

    @Property
    void equalsAndHashCodeFollowUnderlyingValue(@ForAll("nonNullInts") int value) {
        Box<Years, Integer> left = Box.of(value);
        Box<Months, Integer> right = Box.of(value, new Object());

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Property
    void compareValueMatchesIntegerOrdering(@ForAll("nonNullInts") int left, @ForAll("nonNullInts") int right) {
        Box<Years, Integer> years = Box.of(left);
        Box<Months, Integer> months = Box.of(right);

        assertEquals(Integer.compare(left, right), Integer.signum(years.compareValue(months)));
    }

    @Provide
    Arbitrary<Integer> nonNullInts() {
        return Arbitraries.integers();
    }
}
