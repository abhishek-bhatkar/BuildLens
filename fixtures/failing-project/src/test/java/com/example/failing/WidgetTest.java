package com.example.failing;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class WidgetTest {
    @Test
    public void widgetIsHealthy() {
        assertTrue(Widget.healthy());
    }
}
