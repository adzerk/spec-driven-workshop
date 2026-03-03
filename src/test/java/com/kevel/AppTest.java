package com.kevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppTest {
    @Test
    void greet_returnsExpectedMessage() {
        assertEquals("Hello, JUnit!", App.greet("JUnit"));
    }
}
