package com.kevel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class ResultTest {

    enum ValidationError {
        INVALID_INPUT,
        BAD_FORMAT
    }

    @Test
    void okCreatesSuccessResult() {
        Result<Integer, ValidationError> result = Result.ok(42);

        assertTrue(result.isOk());
        assertFalse(result.isErr());
        assertEquals(42, result.get());
    }

    @Test
    void errCreatesFailureResult() {
        Result<Integer, ValidationError> result = Result.err(ValidationError.INVALID_INPUT);

        assertFalse(result.isOk());
        assertTrue(result.isErr());
        assertEquals(7, result.orElse(7));
    }

    @Test
    void okRejectsNullValues() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> Result.ok(null));

        assertTrue(exception.getMessage().contains("Ok value cannot be null"));
    }

    @Test
    void errRejectsNullValues() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> Result.err(null));

        assertTrue(exception.getMessage().contains("Err value cannot be null"));
    }

    @Test
    void errGetThrowsIllegalStateWhenErrorIsNotAnException() {
        Result<Integer, ValidationError> result = Result.err(ValidationError.BAD_FORMAT);

        IllegalStateException exception = assertThrows(IllegalStateException.class, result::get);

        assertTrue(exception.getMessage().contains("Called get() on Err value"));
    }

    @Test
    void errGetRethrowsExceptionErrors() {
        IOException error = new IOException("broken");
        Result<Integer, IOException> result = Result.err(error);

        IOException thrown = assertThrows(IOException.class, result::get);

        assertEquals("broken", thrown.getMessage());
    }

    @Test
    void orElseAndOrComputePreferSuccessValueWhenPresent() {
        Result<Integer, ValidationError> result = Result.ok(10);
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertEquals(10, result.orElse(99));
        assertEquals(10, result.orCompute(error -> {
            invoked.set(true);
            return -1;
        }));
        assertFalse(invoked.get());
    }

    @Test
    void errCanProvideFallbacks() {
        Result<Integer, ValidationError> result = Result.err(ValidationError.INVALID_INPUT);

        assertEquals(99, result.orElse(99));
        assertEquals(-1, result.orCompute(error -> error == ValidationError.INVALID_INPUT ? -1 : -2));
    }

    @Test
    void mapTransformsSuccessValue() {
        Result<String, ValidationError> result =
                Result.<Integer, ValidationError>ok(5).map(Object::toString);

        assertEquals("5", result.get());
    }

    @Test
    void mapPreservesErrorsWithoutInvokingMapper() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Result<String, ValidationError> result = Result.<Integer, ValidationError>err(ValidationError.BAD_FORMAT)
                .map(value -> {
                    invoked.set(true);
                    return value.toString();
                });

        assertFalse(invoked.get());
        assertTrue(result.isErr());
        assertEquals("fallback", result.orElse("fallback"));
    }

    @Test
    void mapRejectsNullMappedValues() {
        Result<Integer, ValidationError> result = Result.ok(5);

        assertThrows(NullPointerException.class, () -> result.map(value -> null));
    }

    @Test
    void flatMapChainsSuccesses() {
        Result<Integer, ValidationError> result =
                Result.<Integer, ValidationError>ok(5).flatMap(value -> Result.<Integer, ValidationError>ok(value + 2));

        assertEquals(7, result.get());
    }

    @Test
    void flatMapCanSwitchToAnError() {
        Result<Integer, ValidationError> result =
                Result.<Integer, ValidationError>ok(5).flatMap(value -> Result.err(ValidationError.BAD_FORMAT));

        assertTrue(result.isErr());
        assertEquals(10, result.orElse(10));
    }

    @Test
    void flatMapSkipsMapperForErrors() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        Result<Integer, ValidationError> result = Result.<Integer, ValidationError>err(ValidationError.INVALID_INPUT)
                .flatMap(value -> {
                    invoked.set(true);
                    return Result.<Integer, ValidationError>ok(value + 1);
                });

        assertFalse(invoked.get());
        assertTrue(result.isErr());
    }

    @Test
    void foldChoosesTheMatchingBranch() {
        Result<Integer, ValidationError> ok = Result.ok(9);
        Result<Integer, ValidationError> err = Result.err(ValidationError.BAD_FORMAT);

        assertEquals("ok:9", ok.fold(value -> "ok:" + value, error -> "err:" + error));
        assertEquals("err:BAD_FORMAT", err.fold(value -> "ok:" + value, error -> "err:" + error));
    }

    @Test
    void toOptionalBridgesToOptional() {
        assertEquals(Optional.of(1), Result.<Integer, ValidationError>ok(1).toOptional());
        assertEquals(
                Optional.empty(),
                Result.<Integer, ValidationError>err(ValidationError.BAD_FORMAT).toOptional());
    }

    @Test
    void ofCapturesSuccessfulOperations() {
        Result<Integer, IOException> result = Result.of(() -> 123);

        assertTrue(result.isOk());
        assertEquals(123, result.get());
    }

    @Test
    void ofCapturesThrownExceptions() {
        Result<Integer, IOException> result = Result.of(() -> {
            throw new IOException("io");
        });

        assertTrue(result.isErr());
        assertEquals(77, result.orElse(77));
        assertEquals(
                "io",
                assertInstanceOf(IOException.class, ((Result.Err<Integer, IOException>) result).error())
                        .getMessage());
    }

    @Test
    void ofWithMapperConvertsExceptionsToDomainErrors() {
        Result<Integer, ValidationError> result = Result.of(
                () -> {
                    throw new IllegalArgumentException("bad");
                },
                ex -> ValidationError.BAD_FORMAT);

        assertTrue(result.isErr());
        assertEquals(-2, result.orCompute(error -> error == ValidationError.BAD_FORMAT ? -2 : -1));
    }

    @Test
    void getCheckedReturnsOkValueAndThrowsCheckedErrors() throws IOException {
        Result<Integer, IOException> ok = Result.ok(3);
        Result<Integer, IOException> err = Result.err(new IOException("checked"));

        assertEquals(3, Result.getChecked(ok));

        IOException thrown = assertThrows(IOException.class, () -> Result.getChecked(err));
        assertEquals("checked", thrown.getMessage());
    }

    @Property
    void okMapIdentityPreservesValue(@ForAll("nonNullStrings") String value) {
        Result<String, ValidationError> result =
                Result.<String, ValidationError>ok(value).map(v -> v);

        assertEquals(value, result.get());
    }

    @Property
    void errMapPreservesError(@ForAll ValidationError error) {
        Result<String, ValidationError> result =
                Result.<Integer, ValidationError>err(error).map(Object::toString);

        assertTrue(result.isErr());
        assertEquals("fallback", result.orElse("fallback"));
        assertEquals(error.name(), result.fold(value -> "unexpected", Enum::name));
    }

    @Property
    void okFlatMapWithOkPreservesValue(@ForAll int value) {
        Result<Integer, ValidationError> result =
                Result.<Integer, ValidationError>ok(value).flatMap(v -> Result.<Integer, ValidationError>ok(v));

        assertEquals(value, result.get());
    }

    @Provide
    Arbitrary<String> nonNullStrings() {
        return Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(40);
    }
}
