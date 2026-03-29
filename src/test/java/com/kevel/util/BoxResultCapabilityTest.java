package com.kevel.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoxResultCapabilityTest {

    interface Anonymous {}

    interface Authenticated {}

    interface MfaVerified extends Authenticated {}

    record Session(String userId) {}

    enum AuthError {
        BAD_PASSWORD,
        BAD_OTP,
        UNTRUSTED_SESSION
    }

    private static final Object TRUSTED_WITNESS = new Object();

    static Box<Anonymous, Session> begin(String userId) {
        return Box.of(new Session(userId), TRUSTED_WITNESS);
    }

    static Result<Box<Authenticated, Session>, AuthError> login(Box<Anonymous, Session> session, String password) {
        if (!session.hasWitness(TRUSTED_WITNESS)) {
            return Result.err(AuthError.UNTRUSTED_SESSION);
        }
        if (!"correct horse battery staple".equals(password)) {
            return Result.err(AuthError.BAD_PASSWORD);
        }
        return Result.ok(session.into(TRUSTED_WITNESS));
    }

    static Result<Box<MfaVerified, Session>, AuthError> verifySecondFactor(
            Box<Authenticated, Session> session, String otp) {
        if (!session.hasWitness(TRUSTED_WITNESS)) {
            return Result.err(AuthError.UNTRUSTED_SESSION);
        }
        if (!"123456".equals(otp)) {
            return Result.err(AuthError.BAD_OTP);
        }
        return Result.ok(session.into(TRUSTED_WITNESS));
    }

    static Result<String, AuthError> transfer(Box<MfaVerified, Session> session, long cents) {
        if (!session.hasWitness(TRUSTED_WITNESS)) {
            return Result.err(AuthError.UNTRUSTED_SESSION);
        }
        return Result.ok(
                "Transferred %d cents for %s".formatted(cents, session.get().userId()));
    }

    @Test
    void happyPathCombinesBoxCapabilitiesWithResultBasedTransitions() {
        Result<String, AuthError> result = login(begin("alice"), "correct horse battery staple")
                .flatMap(session -> verifySecondFactor(session, "123456"))
                .flatMap(session -> transfer(session, 5_000));

        assertTrue(result.isOk());
        assertEquals("Transferred 5000 cents for alice", result.get());
    }

    @Test
    void loginFailureIsReportedAsData() {
        Result<Box<Authenticated, Session>, AuthError> result = login(begin("alice"), "wrong");

        assertTrue(result.isErr());
        assertEquals(AuthError.BAD_PASSWORD, result.fold(box -> null, error -> error));
    }

    @Test
    void secondFactorFailurePreservesTheErrorAsData() {
        Result<Box<MfaVerified, Session>, AuthError> result = login(begin("alice"), "correct horse battery staple")
                .flatMap(session -> verifySecondFactor(session, "000000"));

        assertTrue(result.isErr());
        assertEquals(AuthError.BAD_OTP, result.fold(box -> null, error -> error));
    }

    @Test
    void witnessProtectionRejectsForgedCapabilities() {
        Box<Anonymous, Session> forged = Box.of(new Session("mallory"));

        Result<Box<Authenticated, Session>, AuthError> loginResult = login(forged, "correct horse battery staple");

        assertTrue(loginResult.isErr());
        assertEquals(AuthError.UNTRUSTED_SESSION, loginResult.fold(box -> null, error -> error));
    }

    @Test
    void successfulTransitionsCarryWitnessForward() {
        Result<Box<MfaVerified, Session>, AuthError> verified = login(begin("alice"), "correct horse battery staple")
                .flatMap(session -> verifySecondFactor(session, "123456"));

        assertTrue(verified.isOk());
        assertTrue(verified.get().hasWitness(TRUSTED_WITNESS));
    }

    @Test
    void resultPipelineShortCircuitsAfterFirstFailure() {
        Result<String, AuthError> result = login(begin("alice"), "wrong")
                .flatMap(session -> verifySecondFactor(session, "123456"))
                .flatMap(session -> transfer(session, 5_000));

        assertTrue(result.isErr());
        assertFalse(result.toOptional().isPresent());
        assertEquals(AuthError.BAD_PASSWORD, result.fold(value -> null, error -> error));
    }
}
