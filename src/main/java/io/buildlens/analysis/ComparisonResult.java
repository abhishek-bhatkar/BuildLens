package io.buildlens.analysis;

import io.buildlens.core.model.Category;
import io.buildlens.core.model.Task;

import java.util.List;

/** Result of comparing two captured builds (spec §13). */
public final class ComparisonResult {

    public enum Verdict {
        REGRESSION,
        IMPROVEMENT,
        UNCHANGED
    }

    /** One compared row: previous value, current value, delta. */
    public static final class DeltaRow {
        public final String label;
        public final Long previousMs;
        public final Long currentMs;
        public final long deltaMs;

        public DeltaRow(String label, Long previousMs, Long currentMs, long deltaMs) {
            this.label = label;
            this.previousMs = previousMs;
            this.currentMs = currentMs;
            this.deltaMs = deltaMs;
        }
    }

    private final String previousId;
    private final String currentId;
    private final long previousDurationMs;
    private final long currentDurationMs;
    private final long durationDeltaMs;
    private final double durationDeltaPercent;
    private final Verdict verdict;
    private final List<DeltaRow> categoryRows;
    private final List<DeltaRow> moduleDeltas;
    private final List<TaskDelta> taskRegressions;
    private final List<TaskDelta> taskImprovements;

    public ComparisonResult(String previousId, String currentId,
                            long previousDurationMs, long currentDurationMs,
                            long durationDeltaMs, double durationDeltaPercent,
                            Verdict verdict,
                            List<DeltaRow> categoryRows, List<DeltaRow> moduleDeltas,
                            List<TaskDelta> taskRegressions, List<TaskDelta> taskImprovements) {
        this.previousId = previousId;
        this.currentId = currentId;
        this.previousDurationMs = previousDurationMs;
        this.currentDurationMs = currentDurationMs;
        this.durationDeltaMs = durationDeltaMs;
        this.durationDeltaPercent = durationDeltaPercent;
        this.verdict = verdict;
        this.categoryRows = categoryRows;
        this.moduleDeltas = moduleDeltas;
        this.taskRegressions = taskRegressions;
        this.taskImprovements = taskImprovements;
    }

    /** A task present in both builds with its duration change. */
    public static final class TaskDelta {
        public final Task previous;
        public final Task current;
        public final long deltaMs;

        public TaskDelta(Task previous, Task current, long deltaMs) {
            this.previous = previous;
            this.current = current;
            this.deltaMs = deltaMs;
        }
    }

    public String getPreviousId() {
        return previousId;
    }

    public String getCurrentId() {
        return currentId;
    }

    public long getPreviousDurationMs() {
        return previousDurationMs;
    }

    public long getCurrentDurationMs() {
        return currentDurationMs;
    }

    public long getDurationDeltaMs() {
        return durationDeltaMs;
    }

    public double getDurationDeltaPercent() {
        return durationDeltaPercent;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public List<DeltaRow> getCategoryRows() {
        return categoryRows;
    }

    public List<DeltaRow> getModuleDeltas() {
        return moduleDeltas;
    }

    public List<TaskDelta> getTaskRegressions() {
        return taskRegressions;
    }

    public List<TaskDelta> getTaskImprovements() {
        return taskImprovements;
    }
}
