package io.buildlens.analysis;

import io.buildlens.core.model.Build;
import io.buildlens.core.model.Category;
import io.buildlens.core.model.Module;
import io.buildlens.core.model.Task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares two captured builds: overall duration, per-category, per-module
 * and per-task deltas, with the biggest regressions surfaced first
 * (spec §13). Rows are matched on stable identities (module name, task key)
 * so reordered builds still compare correctly.
 */
public final class BuildComparison {

    /** Deltas below either threshold are treated as unchanged. */
    private static final long MIN_ABS_DELTA_MS = 1000;
    private static final double MIN_PERCENT_DELTA = 5.0;

    public ComparisonResult compare(Build previous, Build current) {
        long prevMs = previous.getDurationMs();
        long curMs = current.getDurationMs();
        long delta = curMs - prevMs;
        double percent = prevMs == 0 ? 0.0 : (delta * 100.0) / prevMs;
        ComparisonResult.Verdict verdict;
        if (Math.abs(delta) < MIN_ABS_DELTA_MS || Math.abs(percent) < MIN_PERCENT_DELTA) {
            verdict = ComparisonResult.Verdict.UNCHANGED;
        } else {
            verdict = delta > 0
                    ? ComparisonResult.Verdict.REGRESSION
                    : ComparisonResult.Verdict.IMPROVEMENT;
        }

        List<ComparisonResult.DeltaRow> categoryRows = categoryRows(previous, current);
        List<ComparisonResult.DeltaRow> moduleDeltas = moduleDeltas(previous, current);

        List<ComparisonResult.TaskDelta> taskDeltas = taskDeltas(previous, current);
        List<ComparisonResult.TaskDelta> regressions = new ArrayList<ComparisonResult.TaskDelta>();
        List<ComparisonResult.TaskDelta> improvements = new ArrayList<ComparisonResult.TaskDelta>();
        for (ComparisonResult.TaskDelta taskDelta : taskDeltas) {
            if (taskDelta.deltaMs > 0) {
                regressions.add(taskDelta);
            } else if (taskDelta.deltaMs < 0) {
                improvements.add(taskDelta);
            }
        }

        return new ComparisonResult(
                previous.getBuildId(), current.getBuildId(),
                prevMs, curMs, delta, percent, verdict,
                categoryRows, moduleDeltas, regressions, improvements);
    }

    private static List<ComparisonResult.DeltaRow> categoryRows(Build previous, Build current) {
        Map<Category, long[]> totals = new LinkedHashMap<Category, long[]>();
        accumulate(totals, previous, 0);
        accumulate(totals, current, 1);

        List<ComparisonResult.DeltaRow> rows =
                new ArrayList<ComparisonResult.DeltaRow>();
        for (Map.Entry<Category, long[]> entry : totals.entrySet()) {
            long prev = entry.getValue()[0];
            long cur = entry.getValue()[1];
            rows.add(new ComparisonResult.DeltaRow(entry.getKey().displayName(), prev, cur, cur - prev));
        }
        rows.sort(new Comparator<ComparisonResult.DeltaRow>() {
            @Override
            public int compare(ComparisonResult.DeltaRow a, ComparisonResult.DeltaRow b) {
                return Long.compare(Math.abs(b.deltaMs), Math.abs(a.deltaMs));
            }
        });
        return rows;
    }

    private static void accumulate(Map<Category, long[]> totals, Build build, int slot) {
        Map<Category, Long> byCategory = new EnumMap<Category, Long>(Category.class);
        for (Task task : build.getTasks()) {
            if (task.getDurationMs() == null || task.getCategory() == null) {
                continue;
            }
            Long sum = byCategory.get(task.getCategory());
            byCategory.put(task.getCategory(), (sum == null ? 0L : sum) + task.getDurationMs());
        }
        // Sort categories by this build's totals so the union order is stable.
        List<Map.Entry<Category, Long>> entries =
                new ArrayList<Map.Entry<Category, Long>>(byCategory.entrySet());
        entries.sort(new Comparator<Map.Entry<Category, Long>>() {
            @Override
            public int compare(Map.Entry<Category, Long> a, Map.Entry<Category, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        for (Map.Entry<Category, Long> entry : entries) {
            long[] pair = totals.get(entry.getKey());
            if (pair == null) {
                pair = new long[2];
                totals.put(entry.getKey(), pair);
            }
            pair[slot] += entry.getValue();
        }
    }

    private static List<ComparisonResult.DeltaRow> moduleDeltas(Build previous, Build current) {
        Map<String, Long> prev = moduleDurationByName(previous);
        Map<String, Long> cur = moduleDurationByName(current);

        List<ComparisonResult.DeltaRow> rows = new ArrayList<ComparisonResult.DeltaRow>();
        for (Map.Entry<String, Long> entry : cur.entrySet()) {
            Long previousMs = prev.get(entry.getKey());
            if (previousMs == null) {
                continue; // module not present in both builds
            }
            rows.add(new ComparisonResult.DeltaRow(entry.getKey(), previousMs,
                    entry.getValue(), entry.getValue() - previousMs));
        }
        rows.sort(new Comparator<ComparisonResult.DeltaRow>() {
            @Override
            public int compare(ComparisonResult.DeltaRow a, ComparisonResult.DeltaRow b) {
                return Long.compare(b.deltaMs, a.deltaMs);
            }
        });
        return rows;
    }

    private static Map<String, Long> moduleDurationByName(Build build) {
        Map<String, Long> byName = new LinkedHashMap<String, Long>();
        for (Module module : build.getModules()) {
            if (module.getDurationMs() != null) {
                byName.put(module.getName(), module.getDurationMs());
            }
        }
        return byName;
    }

    private static List<ComparisonResult.TaskDelta> taskDeltas(Build previous, Build current) {
        Map<String, Task> prevByKey = new LinkedHashMap<String, Task>();
        for (Task task : previous.getTasks()) {
            if (task.getDurationMs() != null) {
                prevByKey.put(task.key(), task);
            }
        }
        List<ComparisonResult.TaskDelta> deltas = new ArrayList<ComparisonResult.TaskDelta>();
        for (Task task : current.getTasks()) {
            Task other = task.getDurationMs() == null ? null : prevByKey.get(task.key());
            if (other != null) {
                deltas.add(new ComparisonResult.TaskDelta(other, task,
                        task.getDurationMs() - other.getDurationMs()));
            }
        }
        deltas.sort(new Comparator<ComparisonResult.TaskDelta>() {
            @Override
            public int compare(ComparisonResult.TaskDelta a, ComparisonResult.TaskDelta b) {
                return Long.compare(b.deltaMs, a.deltaMs);
            }
        });
        return deltas;
    }
}
