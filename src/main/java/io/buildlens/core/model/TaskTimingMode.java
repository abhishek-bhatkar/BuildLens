package io.buildlens.core.model;

/**
 * How per-task durations in this build were obtained. BuildLens never
 * fabricates timings; when the underlying build system runs in parallel and
 * output interleaves, timings are explicitly marked approximate.
 */
public enum TaskTimingMode {
    /** Sequential build; wall-clock arrival deltas attribute time reliably. */
    SEQUENTIAL_ARRIVAL,
    /** Parallel/multi-threaded build; per-task times are approximate. */
    APPROXIMATE_PARALLEL,
    /** No usable task timing was captured for this build. */
    NONE
}
