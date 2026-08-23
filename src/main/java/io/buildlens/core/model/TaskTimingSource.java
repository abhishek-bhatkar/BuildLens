package io.buildlens.core.model;

/**
 * Where a task's timing numbers came from. Persisted per task so reports can
 * distinguish measured values from derived ones even after the capture
 * context is gone.
 */
public enum TaskTimingSource {
    /** Wall-clock delta between the arrival times of Maven's marker lines. */
    ARRIVAL_CLOCK
}
