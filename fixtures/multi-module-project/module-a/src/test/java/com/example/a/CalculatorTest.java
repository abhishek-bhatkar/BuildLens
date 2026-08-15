package com.example.a;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CalculatorTest {
    @Test
    public void addsNumbers() {
        assertEquals(5, Calculator.add(2, 3));
    }
}
