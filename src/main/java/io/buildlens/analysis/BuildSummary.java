package io.buildlens.analysis;

import io.buildlens.core.model.Category;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TestClassResult;

import java.util.List;
import java.util.Map;

/** Aggregated, display-ready view of one captured build. */
public final class BuildSummary {

    private final Map<Category, Long> categoryTotals;
    private final long attributedMs;
    private final long overheadMs;
    private final List<Task> slowestTasks;
    private final List<Module> modulesByDuration;
    private final List<TestClassResult> slowestTestClasses;

    public BuildSummary(Map<Category, Long> categoryTotals, long attributedMs, long overheadMs,
                        List<Task> slowestTasks, List<Module> modulesByDuration,
                        List<TestClassResult> slowestTestClasses) {
        this.categoryTotals = categoryTotals;
        this.attributedMs = attributedMs;
        this.overheadMs = overheadMs;
        this.slowestTasks = slowestTasks;
        this.modulesByDuration = modulesByDuration;
        this.slowestTestClasses = slowestTestClasses;
    }

    /** Non-zero category totals, largest first. */
    public Map<Category, Long> getCategoryTotals() {
        return categoryTotals;
    }

    /** Total wall time attributed to parsed tasks. */
    public long getAttributedMs() {
        return attributedMs;
    }

    /** Wall time not attributed to any task (JVM boot, scanning, etc.). */
    public long getOverheadMs() {
        return overheadMs;
    }

    public List<Task> getSlowestTasks() {
        return slowestTasks;
    }

    public List<Module> getModulesByDuration() {
        return modulesByDuration;
    }

    public List<TestClassResult> getSlowestTestClasses() {
        return slowestTestClasses;
    }
}
