package io.buildlens.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskTimingModeTest {

    @Test
    void exposesConfidenceForSequentialTimings() {
        assertEquals(TaskTimingConfidence.HIGH, TaskTimingMode.SEQUENTIAL_ARRIVAL.confidence());
    }

    @Test
    void exposesConfidenceForParallelTimings() {
        assertEquals(TaskTimingConfidence.LOW, TaskTimingMode.APPROXIMATE_PARALLEL.confidence());
    }

    @Test
    void exposesUnavailableConfidenceWhenNoTaskTimingExists() {
        assertEquals(TaskTimingConfidence.UNAVAILABLE, TaskTimingMode.NONE.confidence());
    }
}
