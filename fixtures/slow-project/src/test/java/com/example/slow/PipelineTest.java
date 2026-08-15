package com.example.slow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PipelineTest {
    @Test
    public void stageOneIsSlow() throws InterruptedException {
        Thread.sleep(1200);
        assertEquals("done", Pipeline.stage());
    }

    @Test
    public void stageTwoIsSlower() throws InterruptedException {
        Thread.sleep(1800);
        assertEquals("done", Pipeline.stage());
    }
}
