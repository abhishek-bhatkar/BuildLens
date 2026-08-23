package io.buildlens.report;

import io.buildlens.analysis.BuildAnalyzer;
import io.buildlens.analysis.ComparisonResult;
import io.buildlens.core.model.Build;
import io.buildlens.core.model.BuildStatus;
import io.buildlens.core.model.Category;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TaskTimingConfidence;
import io.buildlens.core.model.TaskTimingSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timing provenance must be visible wherever task durations are shown:
 * approximate values never render as if they were exact.
 */
class ConsoleReporterTest {

    private static Task task(String plugin, String goal, long durationMs,
                             TaskTimingConfidence confidence) {
        Task task = new Task(0, "maven-" + plugin + "-plugin", "1.0", goal,
                null, "app", Category.OTHER);
        task.setStartMs(0L);
        task.setEndMs(durationMs);
        task.setDurationMs(durationMs);
        task.setTimingSource(TaskTimingSource.ARRIVAL_CLOCK);
        task.setTimingConfidence(confidence);
        return task;
    }

    private static Build build(Task... tasks) {
        Build build = new Build();
        build.setBuildId("2026-08-20T100000");
        build.setStatus(BuildStatus.SUCCESS);
        build.setDurationMs(5000);
        Collections.addAll(build.getTasks(), tasks);
        return build;
    }

    @Test
    void approximateTaskDurationsAreMarkedNotExact() {
        Build build = build(task("compiler", "compile", 1500, TaskTimingConfidence.LOW));

        String summaryText = new ConsoleReporter().runSummary(build, null, 1);
        assertTrue(summaryText.contains("~1.5s"), summaryText);
        assertTrue(summaryText.contains(ConsoleReporter.APPROXIMATE_NOTE), summaryText);

        String reportText = new ConsoleReporter().fullReport(build);
        assertTrue(reportText.contains("~1.5s"), reportText);
        assertTrue(reportText.contains(ConsoleReporter.APPROXIMATE_NOTE), reportText);
    }

    @Test
    void highConfidenceDurationsRenderExactWithoutMarker() {
        Build build = build(task("compiler", "compile", 1500, TaskTimingConfidence.HIGH));
        String summaryText = new ConsoleReporter().runSummary(build, null, 1);
        assertTrue(summaryText.contains("1.5s"));
        assertFalse(summaryText.contains("~1.5s"));
        assertFalse(summaryText.contains(ConsoleReporter.APPROXIMATE_NOTE));
    }

    @Test
    void untimedTasksAreExcludedAndDoNotBreakRendering() {
        Task untimed = task("jar", "jar", 0, TaskTimingConfidence.UNAVAILABLE);
        untimed.setDurationMs(null);
        untimed.setStartMs(null);
        untimed.setEndMs(null);
        String summaryText = new ConsoleReporter().runSummary(build(untimed), null, 1);
        assertFalse(summaryText.contains("n/a"));
        assertTrue(summaryText.contains("Tasks"));
    }

    @Test
    void comparisonMarksDeltasFromApproximateTimings() {
        ComparisonResult result = compare(
                task("surefire", "test", 2000, TaskTimingConfidence.LOW),
                task("surefire", "test", 3500, TaskTimingConfidence.HIGH));

        String compareText = new ConsoleReporter().compare(result);
        assertTrue(compareText.contains("~app : surefire:test"), compareText);
        assertTrue(compareText.contains(ConsoleReporter.APPROXIMATE_NOTE), compareText);
    }

    @Test
    void comparisonOfExactTimingsHasNoMarker() {
        ComparisonResult result = compare(
                task("surefire", "test", 2000, TaskTimingConfidence.HIGH),
                task("surefire", "test", 3500, TaskTimingConfidence.HIGH));

        String compareText = new ConsoleReporter().compare(result);
        assertTrue(compareText.contains("app : surefire:test"));
        assertFalse(compareText.contains("~app : surefire:test"));
        assertFalse(compareText.contains(ConsoleReporter.APPROXIMATE_NOTE));
    }

    private static ComparisonResult compare(Task before, Task after) {
        long delta = after.getDurationMs() - before.getDurationMs();
        ComparisonResult.TaskDelta taskDelta =
                new ComparisonResult.TaskDelta(before, after, delta);
        return new ComparisonResult("prev", "curr",
                before.getDurationMs(), after.getDurationMs(),
                delta, 50.0, delta > 0 ? ComparisonResult.Verdict.REGRESSION
                        : ComparisonResult.Verdict.IMPROVEMENT,
                new ArrayList<ComparisonResult.DeltaRow>(),
                new ArrayList<ComparisonResult.DeltaRow>(),
                delta > 0 ? Collections.singletonList(taskDelta)
                        : new ArrayList<ComparisonResult.TaskDelta>(),
                delta < 0 ? Collections.singletonList(taskDelta)
                        : new ArrayList<ComparisonResult.TaskDelta>());
    }
}
