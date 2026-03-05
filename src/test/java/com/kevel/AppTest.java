package com.kevel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void increasingSum_expectedUsage() {
        assertEquals(30, App.increasingSum(10, 20));
        assertEquals(40, App.increasingSum(20, 20));
        assertEquals(21, App.increasingSum(3, 20));
    }
}
