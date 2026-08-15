package com.example.failing;

public class Widget {
    public static boolean healthy() {
        // Deliberately broken so the fixture build fails at the test phase.
        return false;
    }
}
