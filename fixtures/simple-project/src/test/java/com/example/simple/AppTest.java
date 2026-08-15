package com.example.simple;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppTest {
    @Test
    public void greetingIsStable() {
        assertEquals("hello from simple-project", App.greeting());
    }

    @Test
    public void greetingIsNotEmpty() {
        assertEquals(false, App.greeting().isEmpty());
    }
}
