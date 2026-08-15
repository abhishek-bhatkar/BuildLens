package io.buildlens.analysis;

import io.buildlens.core.model.Build;
import io.buildlens.core.model.Category;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;
import io.buildlens.core.model.TestClassResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns a captured {@link Build} into ranked aggregates (spec §14, §15, §18). */
public final class BuildAnalyzer {

    private static final int DEFAULT_TOP = 10;

    public BuildSummary summarize(Build build) {
        return summarize(build, DEFAULT_TOP);
    }

    public BuildSummary summarize(Build build, int topN) {
        Map<Category, Long> totals = categoryTotals(build.getTasks());
        long attributedMs = sumTaskMillis(build.getTasks());

        List<Task> slowest = new ArrayList<Task>(tasksWithTiming(build.getTasks()));
        slowest.sort(new Comparator<Task>() {
            @Override
            public int compare(Task a, Task b) {
                return Long.compare(b.getDurationMs(), a.getDurationMs());
            }
        });
        slowest = slowest.subList(0, Math.min(topN, slowest.size()));

        List<Module> modules = new ArrayList<Module>(build.getModules());
        modules.sort(new Comparator<Module>() {
            @Override
            public int compare(Module a, Module b) {
                long av = a.getDurationMs() == null ? -1L : a.getDurationMs();
                long bv = b.getDurationMs() == null ? -1L : b.getDurationMs();
                return Long.compare(bv, av);
            }
        });

        List<TestClassResult> slowestTests = new ArrayList<TestClassResult>(build.getTests());
        slowestTests.sort(new Comparator<TestClassResult>() {
            @Override
            public int compare(TestClassResult a, TestClassResult b) {
                long av = a.getTimeElapsedMs() == null ? -1L : a.getTimeElapsedMs();
                long bv = b.getTimeElapsedMs() == null ? -1L : b.getTimeElapsedMs();
                return Long.compare(bv, av);
            }
        });
        slowestTests = slowestTests.subList(0, Math.min(topN, slowestTests.size()));

        long overhead = Math.max(0L, build.getDurationMs() - attributedMs);
        return new BuildSummary(totals, attributedMs, overhead, slowest, modules, slowestTests);
    }

    /** Category totals over timed tasks, largest total first. */
    public static Map<Category, Long> categoryTotals(List<Task> tasks) {
        final Map<Category, Long> totals = new EnumMap<Category, Long>(Category.class);
        for (Task task : tasks) {
            if (task.getDurationMs() == null || task.getCategory() == null) {
                continue;
            }
            Long current = totals.get(task.getCategory());
            totals.put(task.getCategory(), (current == null ? 0L : current) + task.getDurationMs());
        }
        List<Map.Entry<Category, Long>> entries =
                new ArrayList<Map.Entry<Category, Long>>(totals.entrySet());
        entries.sort(new Comparator<Map.Entry<Category, Long>>() {
            @Override
            public int compare(Map.Entry<Category, Long> a, Map.Entry<Category, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        Map<Category, Long> ordered = new LinkedHashMap<Category, Long>();
        for (Map.Entry<Category, Long> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private static long sumTaskMillis(List<Task> tasks) {
        long sum = 0L;
        for (Task task : tasks) {
            if (task.getDurationMs() != null) {
                sum += task.getDurationMs();
            }
        }
        return sum;
    }

    private static List<Task> tasksWithTiming(List<Task> tasks) {
        List<Task> timed = new ArrayList<Task>();
        for (Task task : tasks) {
            if (task.getDurationMs() != null) {
                timed.add(task);
            }
        }
        return timed;
    }
}
